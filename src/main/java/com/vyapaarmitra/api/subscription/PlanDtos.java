package com.vyapaarmitra.api.subscription;

public final class PlanDtos {

    private PlanDtos() {
    }

    /**
     * The client-facing plan projection — the exact shape the web's PlanView expects
     * (docs/subscriptions.md §4.1). Entitlements are intentionally omitted; the client
     * derives them locally from the tier so infinite caps never cross the wire.
     */
    public record PlanView(PlanTier plan, PlanTier basePlan, boolean isTrial,
                           int trialDaysLeft, Usage usage) {
    }

    public record Usage(long entriesToday, long pakkaThisMonth) {
    }
}
