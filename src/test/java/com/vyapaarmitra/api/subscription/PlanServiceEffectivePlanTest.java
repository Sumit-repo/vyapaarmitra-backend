package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * effectivePlan is pure (no DB), so exercise the trial / paid / grace / cancel
 * windows directly — this is the money math that decides what a shop can do.
 */
class PlanServiceEffectivePlanTest {

    // effectivePlan(sub, now) touches no collaborators, so nulls are fine here.
    private final PlanService service = new PlanService(null, null, null, null);

    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    private Subscription sub(SubscriptionStatus status, PlanTier plan) {
        Subscription s = new Subscription();
        s.setStatus(status);
        s.setPlan(plan);
        s.setTrialEndsAt(NOW.minus(30, ChronoUnit.DAYS)); // trial long over unless overridden
        return s;
    }

    @Test
    void trialingWithinWindowIsPro() {
        Subscription s = sub(SubscriptionStatus.TRIALING, PlanTier.FREE);
        s.setTrialEndsAt(NOW.plus(5, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.PRO);
    }

    @Test
    void expiredTrialFallsToFree() {
        Subscription s = sub(SubscriptionStatus.TRIALING, PlanTier.FREE);
        s.setTrialEndsAt(NOW.minus(1, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void activeWithinPeriodKeepsPaidPlan() {
        Subscription s = sub(SubscriptionStatus.ACTIVE, PlanTier.PRO);
        s.setCurrentPeriodEnd(NOW.plus(10, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.PRO);
    }

    @Test
    void activePastPeriodEndFallsToFree() {
        Subscription s = sub(SubscriptionStatus.ACTIVE, PlanTier.LITE);
        s.setCurrentPeriodEnd(NOW.minus(1, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void pastDueWithinGraceKeepsPlan() {
        Subscription s = sub(SubscriptionStatus.PAST_DUE, PlanTier.PRO);
        s.setCurrentPeriodEnd(NOW.minus(2, ChronoUnit.DAYS));
        s.setGraceUntil(NOW.plus(5, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.PRO);
    }

    @Test
    void pastDueAfterGraceFallsToFree() {
        Subscription s = sub(SubscriptionStatus.PAST_DUE, PlanTier.PRO);
        s.setGraceUntil(NOW.minus(1, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void cancelledKeepsAccessUntilPeriodEnd() {
        Subscription s = sub(SubscriptionStatus.CANCELLED, PlanTier.PRO);
        s.setCurrentPeriodEnd(NOW.plus(3, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.PRO);
    }

    @Test
    void cancelledPastPeriodEndFallsToFree() {
        Subscription s = sub(SubscriptionStatus.CANCELLED, PlanTier.PRO);
        s.setCurrentPeriodEnd(NOW.minus(1, ChronoUnit.DAYS));
        assertThat(service.effectivePlan(s, NOW)).isEqualTo(PlanTier.FREE);
    }
}
