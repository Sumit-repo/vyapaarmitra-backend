package com.vyapaarmitra.api.subscription;

import java.util.Map;

/**
 * Single backend source of tiers, entitlements, and pricing. Kept in lockstep with
 * the web's lib/plan-model.ts and the table in docs/subscriptions.md §2 —
 * {@code PlanCatalogTest} asserts the numbers match the spec. Infinite caps use
 * {@link Integer#MAX_VALUE}.
 */
public final class PlanCatalog {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    private PlanCatalog() {
    }

    /** Paid, gated capabilities. Maps 1:1 to the web's UpgradeReason feature set. */
    public enum Feature {
        REPORTS, RECOVERY, AUTOMATION, STAFF, TRUST_ANALYTICS
    }

    public record Entitlements(int dailyEntryCap, int pakkaMonthlyCap, int maxBranches,
                               boolean reports, boolean recovery, boolean automation,
                               boolean staff, boolean trustAnalytics) {

        public boolean has(Feature feature) {
            return switch (feature) {
                case REPORTS -> reports;
                case RECOVERY -> recovery;
                case AUTOMATION -> automation;
                case STAFF -> staff;
                case TRUST_ANALYTICS -> trustAnalytics;
            };
        }
    }

    /** ₹ amounts. Annual is billed for 10 months (2 free). */
    public record Price(int monthly, int annual) {
    }

    private static final Map<PlanTier, Entitlements> ENTITLEMENTS = Map.of(
        PlanTier.FREE, new Entitlements(25, 3, 1, false, false, false, false, false),
        PlanTier.LITE, new Entitlements(100, UNLIMITED, 2, true, false, false, false, false),
        PlanTier.PRO, new Entitlements(UNLIMITED, UNLIMITED, UNLIMITED, true, true, true, true, true)
    );

    private static final Map<PlanTier, Price> PRICING = Map.of(
        PlanTier.LITE, new Price(49, 490),
        PlanTier.PRO, new Price(99, 990)
    );

    public static Entitlements entitlements(PlanTier plan) {
        return ENTITLEMENTS.get(plan);
    }

    /** Price for a paid tier, or null for FREE. */
    public static Price price(PlanTier plan) {
        return PRICING.get(plan);
    }
}
