package com.vyapaarmitra.api.trust;

import static org.assertj.core.api.Assertions.assertThat;

import com.vyapaarmitra.api.customer.TrustBucket;
import com.vyapaarmitra.api.trust.TrustScoreService.TrustResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TrustScoreServiceTest {

    private final TrustScoreService service = new TrustScoreService();

    @Test
    void newCustomerIsNeutralWatch() {
        TrustResult result = service.compute(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        assertThat(result.score()).isEqualTo(60);
        assertThat(result.bucket()).isEqualTo(TrustBucket.WATCH);
    }

    @Test
    void perfectRepayerIsGood() {
        TrustResult result = service.compute(new BigDecimal("1000"), new BigDecimal("1000"), 0, 0);
        assertThat(result.score()).isEqualTo(90);
        assertThat(result.bucket()).isEqualTo(TrustBucket.GOOD);
    }

    @Test
    void longOverdueNonPayerIsRisky() {
        TrustResult result = service.compute(new BigDecimal("1000"), BigDecimal.ZERO, 60, 3);
        // 55 + 0 - 45 (capped) - 15 (capped) = -5 -> clamped to 0
        assertThat(result.score()).isZero();
        assertThat(result.bucket()).isEqualTo(TrustBucket.RISKY);
    }

    @Test
    void partialRepayerSlightlyOverdueIsWatch() {
        TrustResult result = service.compute(new BigDecimal("1000"), new BigDecimal("600"), 5, 1);
        // 55 + 21 - 7.5 - 5 = 63.5 -> 64
        assertThat(result.score()).isEqualTo(64);
        assertThat(result.bucket()).isEqualTo(TrustBucket.WATCH);
    }

    @Test
    void scoreIsClampedTo100() {
        TrustResult result = service.compute(new BigDecimal("100"), new BigDecimal("500"), 0, 0);
        assertThat(result.score()).isEqualTo(90);
    }
}
