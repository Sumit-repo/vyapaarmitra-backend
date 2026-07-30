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

    /**
     * A GST invoice / receipt for a subscription charge, from Razorpay. {@code amount}
     * is in paise; {@code issuedAt} is epoch seconds (nullable); {@code shortUrl} is the
     * hosted, GSTIN-bearing invoice the shopkeeper can view/download.
     */
    public record InvoiceItem(String id, String status, long amount, Long issuedAt, String shortUrl) {
    }
}
