package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.subscription.BillingDtos.CheckoutResponse;
import com.vyapaarmitra.api.subscription.BillingDtos.InvoiceItem;
import com.vyapaarmitra.api.subscription.RazorpayClient.RazorpaySubscription;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    // Razorpay requires a bounded total_count. Use a long horizon for monthly so the
    // mandate effectively runs "until cancelled"; yearly renews for a decade.
    private static final int MONTHLY_CYCLES = 120;
    private static final int YEARLY_CYCLES = 10;

    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final BusinessRepository businessRepository;

    public BillingService(SubscriptionRepository subscriptionRepository, PlanService planService,
                          RazorpayClient razorpayClient, RazorpayProperties razorpayProperties,
                          BusinessRepository businessRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planService = planService;
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
        this.businessRepository = businessRepository;
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

        // If the shop is GST-registered, pre-create a Razorpay customer carrying the
        // GSTIN and tie the subscription to it, so the SaaS-charge invoice is GST-valid.
        String customerId = gstinCustomerId(authUser.businessId());

        int totalCount = period == BillingPeriod.YEARLY ? YEARLY_CYCLES : MONTHLY_CYCLES;
        RazorpaySubscription created =
            razorpayClient.createSubscription(planId, totalCount, authUser.businessId(), customerId);

        // Store the intent ONLY. plan/billingPeriod are applied on the verified
        // subscription.activated/charged webhook (RazorpayWebhookService) — never here.
        // Writing setPlan(plan) here would grant the tier immediately to an already-
        // ACTIVE customer upgrading (effectivePlan returns sub.getPlan() while ACTIVE),
        // so a cancelled/unpaid checkout would leak paid access.
        Subscription sub = planService.getOrCreate(authUser.businessId());
        sub.setGateway("RAZORPAY");
        sub.setGatewaySubId(created.id());
        if (customerId != null) {
            sub.setGatewayCustomerId(customerId);
        }
        sub.setPendingPlan(plan);
        sub.setPendingBillingPeriod(period);
        subscriptionRepository.save(sub);

        return new CheckoutResponse(created.id(), created.shortUrl(), razorpayProperties.keyId());
    }

    /**
     * If the business has a GSTIN, ensures a Razorpay customer exists for it and returns
     * its id (so the invoice carries the GSTIN). Returns null when the shop isn't GST-
     * registered — checkout then proceeds with a gateway-created customer as before.
     */
    private String gstinCustomerId(java.util.UUID businessId) {
        Business business = businessRepository.findById(businessId).orElse(null);
        if (business == null || business.getGstin() == null || business.getGstin().isBlank()) {
            return null;
        }
        // Non-fatal: if Razorpay rejects the GSTIN customer (bad GSTIN, test-mode quirk),
        // don't block payment — proceed without the GST-customer, as documented.
        try {
            return razorpayClient.createCustomerWithGstin(business.getName(), business.getGstin());
        } catch (ApiException e) {
            log.warn("GSTIN customer create failed for business {} — continuing checkout without it",
                businessId, e);
            return null;
        }
    }

    /** GST invoices/receipts for the caller's subscription — empty until they've paid. */
    @Transactional(readOnly = true)
    public List<InvoiceItem> invoices(AuthUser authUser) {
        Subscription sub = subscriptionRepository.findByBusinessId(authUser.businessId()).orElse(null);
        if (sub == null || sub.getGatewaySubId() == null) {
            return List.of();
        }
        return razorpayClient.listInvoices(sub.getGatewaySubId()).stream()
            .map(i -> new InvoiceItem(i.id(), i.status(), i.amount(), i.issuedAt(), i.shortUrl()))
            .toList();
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
