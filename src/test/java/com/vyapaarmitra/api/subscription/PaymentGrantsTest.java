package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/** The shared grant step behind the webhook AND the pull-based /billing/verify. */
class PaymentGrantsTest {

    private Subscription subWithPending() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPlan(PlanTier.LITE);
        sub.setBillingPeriod(BillingPeriod.MONTHLY);
        sub.setCurrentPeriodEnd(Instant.now().plus(10, ChronoUnit.DAYS));
        sub.setPendingPlan(PlanTier.PRO);
        sub.setPendingBillingPeriod(BillingPeriod.YEARLY);
        return sub;
    }

    @Test
    void grantsAndClearsThePendingIntent() {
        Subscription sub = subWithPending();
        Instant existingEnd = sub.getCurrentPeriodEnd();

        boolean applied = PaymentGrants.grant(sub);

        assertThat(applied).isTrue();
        assertThat(sub.getPlan()).isEqualTo(PlanTier.PRO);
        assertThat(sub.getBillingPeriod()).isEqualTo(BillingPeriod.YEARLY);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getGraceUntil()).isNull();
        // Stacked from the existing end, not from now.
        assertThat(sub.getCurrentPeriodEnd())
            .isEqualTo(existingEnd.atZone(ZoneOffset.UTC).plusMonths(12).toInstant());
        assertThat(sub.getPendingPlan()).isNull();
        assertThat(sub.getPendingBillingPeriod()).isNull();
    }

    @Test
    void startsFromNowWhenThePlanHasAlreadyLapsed() {
        Subscription sub = subWithPending();
        sub.setCurrentPeriodEnd(Instant.now().minus(2, ChronoUnit.DAYS));

        Instant before = Instant.now();
        PaymentGrants.grant(sub);

        assertThat(sub.getCurrentPeriodEnd())
            .isAfter(before.plus(360, ChronoUnit.DAYS))
            .isBefore(before.plus(371, ChronoUnit.DAYS));
    }

    @Test
    void secondApplyIsANoOpSoWebhookAndVerifyRaceStaysSafe() {
        Subscription sub = subWithPending();
        PaymentGrants.grant(sub);
        Instant grantedEnd = sub.getCurrentPeriodEnd();

        // The webhook lost the race but retried anyway — it must not stack a second period.
        boolean second = PaymentGrants.grant(sub);

        assertThat(second).isFalse();
        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(grantedEnd);
        assertThat(sub.getPlan()).isEqualTo(PlanTier.PRO);
    }
}