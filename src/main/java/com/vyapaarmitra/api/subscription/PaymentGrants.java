package com.vyapaarmitra.api.subscription;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The one implementation of "apply a paid one-time purchase" — used by BOTH grant
 * paths: the verified {@code payment_link.paid} webhook ({@link RazorpayWebhookService})
 * and the client-driven {@code POST /billing/verify} pull (
 * {@link BillingService#verify}). Applies the chosen tier and STACKS the purchased
 * period onto any remaining time. Mutates the entity in place; the caller (already
 * transactional, holding the row locked — see the pessimistic lookups on
 * {@link SubscriptionRepository}) saves it.
 *
 * <p>Callers must pre-check the grant itself is warranted; this returns false when
 * {@code pendingPlan} is null (nothing pending / another path already granted) so a
 * webhook + verify race de-dupes on the row lock instead of double-stacking.
 */
final class PaymentGrants {

    private PaymentGrants() {
    }

    /** Applies the pending plan/period to the subscription. False = nothing pending. */
    static boolean grant(Subscription sub) {
        PlanTier pendingPlan = sub.getPendingPlan();
        if (pendingPlan == null) {
            return false; // no pending intent (stale/duplicate, or already granted)
        }
        Instant now = Instant.now();
        Instant base = (sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isAfter(now))
            ? sub.getCurrentPeriodEnd() : now;
        int months = sub.getPendingBillingPeriod() == BillingPeriod.YEARLY ? 12 : 1;
        Instant newEnd = base.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();

        sub.setPlan(pendingPlan);
        sub.setBillingPeriod(sub.getPendingBillingPeriod());
        sub.setCurrentPeriodEnd(newEnd);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setGraceUntil(null);
        sub.setPendingPlan(null);
        sub.setPendingBillingPeriod(null);
        return true;
    }
}