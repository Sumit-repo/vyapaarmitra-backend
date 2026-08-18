package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.subscription.BillingDtos.CheckoutResponse;
import com.vyapaarmitra.api.subscription.BillingDtos.InvoiceItem;
import com.vyapaarmitra.api.subscription.RazorpayClient.RazorpayPaymentLink;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write side of billing that the merchant drives. Plans are bought as one-time payments
 * (no auto-renew): checkout creates a hosted Razorpay payment link, and the verified
 * {@code payment_link.paid} webhook (see {@link RazorpayWebhookService}) extends access —
 * stacking the period onto any remaining time. Nothing here flips the effective plan directly.
 */
@Service
public class BillingService {

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
        PlanCatalog.Price price = PlanCatalog.price(plan);
        if (price == null) {
            throw ApiException.badRequest("INVALID_PLAN", "This plan isn't purchasable.");
        }
        long amountPaise = (period == BillingPeriod.YEARLY ? price.annual() : price.monthly()) * 100L;
        // Unique per checkout so the webhook can tie the payment back to this attempt.
        String referenceId = "vm-" + authUser.businessId() + "-" + System.currentTimeMillis();
        String description = "VyapaarMitra " + plan.name() + " — "
            + (period == BillingPeriod.YEARLY ? "1 year" : "1 month");

        RazorpayPaymentLink link =
            razorpayClient.createPaymentLink(amountPaise, description, authUser.businessId(), referenceId);

        // Store the intent ONLY. plan/billingPeriod + the extended period end are applied on the
        // verified payment_link.paid webhook (RazorpayWebhookService) — never here — so an
        // unpaid/abandoned checkout can't grant access.
        Subscription sub = planService.getOrCreate(authUser.businessId());
        sub.setGateway("RAZORPAY");
        sub.setGatewaySubId(link.id()); // payment link id (plink_...)
        sub.setPendingPlan(plan);
        sub.setPendingBillingPeriod(period);
        subscriptionRepository.save(sub);

        return new CheckoutResponse(link.id(), link.shortUrl(), razorpayProperties.keyId());
    }

    /**
     * Receipts for the caller's purchases. One-time payment links don't produce the
     * subscription-style invoice feed we used before; the hosted Razorpay receipt is the
     * record. Returns empty for now (see docs/TODO — wire payment-link receipts if needed).
     */
    @Transactional(readOnly = true)
    public List<InvoiceItem> invoices(AuthUser authUser) {
        return List.of();
    }

    /**
     * Plans are one-time and never auto-renew — there's nothing to cancel; access simply
     * lapses at {@code current_period_end}. Kept as a clear signal for any legacy caller.
     */
    @Transactional(readOnly = true)
    public void cancel(AuthUser authUser) {
        throw ApiException.badRequest("NO_AUTORENEW",
            "This plan doesn't auto-renew — it expires on its own, so there's nothing to cancel.");
    }
}
