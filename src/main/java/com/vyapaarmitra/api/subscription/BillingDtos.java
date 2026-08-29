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
     * Result of POST /billing/verify — the buyer's client calls it after returning from
     * the hosted payment page, to grant the purchase even if the webhook is late/lost.
     * {@code status}: {@code ACTIVATED} (grant applied by this call), {@code ALREADY_ACTIVE}
     * (webhook beat us), or {@code PENDING} (nothing paid yet — keep waiting/retrying).
     * {@code plan} is always the current {@link PlanDtos.PlanView} so clients can refresh
     * their plan cache from the same response.
     */
    public record VerifyResponse(String status, PlanDtos.PlanView plan) {
    }

    /**
     * A GST invoice / receipt for a subscription charge, from Razorpay. {@code amount}
     * is in paise; {@code issuedAt} is epoch seconds (nullable); {@code shortUrl} is the
     * hosted, GSTIN-bearing invoice the shopkeeper can view/download.
     */
    public record InvoiceItem(String id, String status, long amount, Long issuedAt, String shortUrl) {
    }
}
