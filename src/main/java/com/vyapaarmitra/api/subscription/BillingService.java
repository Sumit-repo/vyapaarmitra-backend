package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.subscription.BillingDtos.CheckoutResponse;
import com.vyapaarmitra.api.subscription.RazorpayClient.RazorpaySubscription;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write side of billing that the merchant drives: start a subscription
 * (checkout) and cancel one. Neither flips the effective plan directly —
 * activation is the webhook's job (see {@link RazorpayWebhookController}); cancel
 * only marks CANCELLED, and access continues until current_period_end.
 */
@Service
public class BillingService {

    // Razorpay requires a bounded total_count. Use a long horizon for monthly so the
    // mandate effectively runs "until cancelled"; yearly renews for a decade.
    private static final int MONTHLY_CYCLES = 120;
    private static final int YEARLY_CYCLES = 10;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;

    public BillingService(SubscriptionRepository subscriptionRepository, PlanService planService,
                          RazorpayClient razorpayClient, RazorpayProperties razorpayProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.planService = planService;
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
    }

    @Transactional
    public CheckoutResponse checkout(AuthUser authUser, PlanTier plan, BillingPeriod period) {
        if (plan == PlanTier.FREE) {
            throw ApiException.badRequest("INVALID_PLAN", "Free is not a purchasable plan.");
        }
        if (!razorpayProperties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_DISABLED",
                "Online payments aren't set up yet.");
        }
        String planId = razorpayProperties.planId(plan, period);
        if (planId == null || planId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLAN_NOT_CONFIGURED",
                "This plan isn't available for purchase right now.");
        }

        int totalCount = period == BillingPeriod.YEARLY ? YEARLY_CYCLES : MONTHLY_CYCLES;
        RazorpaySubscription created =
            razorpayClient.createSubscription(planId, totalCount, authUser.businessId());

        // Remember the intent so the webhook knows which plan to activate. The plan
        // does NOT take effect until a verified subscription.activated/charged arrives.
        Subscription sub = planService.getOrCreate(authUser.businessId());
        sub.setGateway("RAZORPAY");
        sub.setGatewaySubId(created.id());
        sub.setPlan(plan);
        sub.setBillingPeriod(period);
        subscriptionRepository.save(sub);

        return new CheckoutResponse(created.id(), created.shortUrl(), razorpayProperties.keyId());
    }

    @Transactional
    public void cancel(AuthUser authUser) {
        Subscription sub = planService.getOrCreate(authUser.businessId());
        if (sub.getGatewaySubId() == null) {
            throw ApiException.badRequest("NO_SUBSCRIPTION", "There's no active subscription to cancel.");
        }
        razorpayClient.cancelAtCycleEnd(sub.getGatewaySubId());
        // Access continues until current_period_end — effectivePlan honours CANCELLED.
        sub.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(sub);
    }
}
