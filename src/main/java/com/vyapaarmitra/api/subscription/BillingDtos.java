package com.vyapaarmitra.api.subscription;

import jakarta.validation.constraints.NotNull;

public final class BillingDtos {

    private BillingDtos() {
    }

    /** Start a subscription for a paid tier (FREE is rejected in the service). */
    public record CheckoutRequest(@NotNull PlanTier plan, @NotNull BillingPeriod period) {
    }

    /** What the client needs to open Razorpay's hosted checkout. */
    public record CheckoutResponse(String subscriptionId, String shortUrl, String keyId) {
    }
}
