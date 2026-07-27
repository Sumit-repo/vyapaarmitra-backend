package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.vyapaarmitra.api.subscription.PlanCatalog.Entitlements;
import com.vyapaarmitra.api.subscription.PlanCatalog.Feature;
import com.vyapaarmitra.api.subscription.PlanCatalog.Price;
import org.junit.jupiter.api.Test;

/**
 * Locks the tier table to docs/subscriptions.md §2 and the web's lib/plan-model.ts.
 * If these drift, billing and the paywall silently disagree — so pin them here.
 */
class PlanCatalogTest {

    @Test
    void freeTierMatchesSpec() {
        Entitlements free = PlanCatalog.entitlements(PlanTier.FREE);
        assertThat(free.dailyEntryCap()).isEqualTo(25);
        assertThat(free.pakkaMonthlyCap()).isEqualTo(3);
        assertThat(free.maxBranches()).isEqualTo(1);
        assertThat(free.reports()).isFalse();
        assertThat(free.recovery()).isFalse();
        assertThat(free.automation()).isFalse();
        assertThat(free.staff()).isFalse();
        assertThat(free.trustAnalytics()).isFalse();
    }

    @Test
    void liteTierMatchesSpec() {
        Entitlements lite = PlanCatalog.entitlements(PlanTier.LITE);
        assertThat(lite.dailyEntryCap()).isEqualTo(100);
        assertThat(lite.pakkaMonthlyCap()).isEqualTo(PlanCatalog.UNLIMITED);
        assertThat(lite.maxBranches()).isEqualTo(2);
        assertThat(lite.reports()).isTrue();
        assertThat(lite.recovery()).isTrue();
        assertThat(lite.automation()).isFalse();
        assertThat(lite.staff()).isFalse();
        assertThat(lite.trustAnalytics()).isFalse();
    }

    @Test
    void proTierIsUnlimitedWithEverything() {
        Entitlements pro = PlanCatalog.entitlements(PlanTier.PRO);
        assertThat(pro.dailyEntryCap()).isEqualTo(PlanCatalog.UNLIMITED);
        assertThat(pro.pakkaMonthlyCap()).isEqualTo(PlanCatalog.UNLIMITED);
        assertThat(pro.maxBranches()).isEqualTo(PlanCatalog.UNLIMITED);
        for (Feature f : Feature.values()) {
            assertThat(pro.has(f)).as("PRO should have %s", f).isTrue();
        }
    }

    @Test
    void pricingMatchesSpec() {
        assertThat(PlanCatalog.price(PlanTier.LITE)).isEqualTo(new Price(49, 490));
        assertThat(PlanCatalog.price(PlanTier.PRO)).isEqualTo(new Price(99, 990));
        assertThat(PlanCatalog.price(PlanTier.FREE)).isNull();
    }
}
