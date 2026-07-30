package com.vyapaarmitra.api.subscription;

import java.time.Instant;

public final class PlanDtos {

    private PlanDtos() {
    }

    /**
     * The client-facing plan projection — the exact shape the web's PlanView expects
     * (docs/subscriptions.md §4.1). Entitlements are intentionally omitted; the client
     * derives them locally from the tier so infinite caps never cross the wire.
     *
     * <p>The billing fields ({@code billingPeriod}, {@code currentPeriodEnd},
     * {@code cancelAtPeriodEnd}) let the web Settings → Plan card show "renews &lt;date&gt;"
     * / "access until &lt;date&gt;" and the exact ₹/period. They are {@code null}/false on
     * FREE and during the trial.
     */
    public record PlanView(PlanTier plan, PlanTier basePlan, boolean isTrial,
                           int trialDaysLeft, Usage usage,
                           BillingPeriod billingPeriod, Instant currentPeriodEnd,
                           boolean cancelAtPeriodEnd) {
    }

    public record Usage(long entriesToday, long pakkaThisMonth) {
    }
}
