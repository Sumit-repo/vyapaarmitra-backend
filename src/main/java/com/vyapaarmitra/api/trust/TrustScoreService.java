package com.vyapaarmitra.api.trust;

import com.vyapaarmitra.api.customer.TrustBucket;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Deterministic v1 trust score. Intentionally simple and explainable so the
 * shopkeeper can trust it:
 *
 *   score = 55 + 35 * paymentRatio - overduePenalty - openOverduePenalty
 *
 * - paymentRatio: lifetime payments / lifetime credits, capped at 1.
 * - overduePenalty: 1.5 points per day the oldest open credit is overdue, capped at 45.
 * - openOverduePenalty: 5 points per overdue open credit, capped at 15.
 *
 * Clamped to 0–100. A brand-new customer with no credits scores 60 (WATCH):
 * neutral until behavior proves otherwise.
 */
@Service
public class TrustScoreService {

    public record TrustResult(int score, TrustBucket bucket) {
    }

    public TrustResult compute(BigDecimal totalCredits, BigDecimal totalPayments,
                               long overdueDays, int openOverdueCredits) {
        if (totalCredits.compareTo(BigDecimal.ZERO) <= 0) {
            return new TrustResult(60, TrustBucket.WATCH);
        }
        double paymentRatio = Math.min(1.0,
            totalPayments.divide(totalCredits, 4, RoundingMode.HALF_UP).doubleValue());
        double overduePenalty = Math.min(45.0, overdueDays * 1.5);
        double openOverduePenalty = Math.min(15.0, openOverdueCredits * 5.0);

        int score = (int) Math.round(55 + 35 * paymentRatio - overduePenalty - openOverduePenalty);
        score = Math.max(0, Math.min(100, score));
        return new TrustResult(score, bucketFor(score));
    }

    private TrustBucket bucketFor(int score) {
        if (score >= 70) {
            return TrustBucket.GOOD;
        }
        if (score >= 40) {
            return TrustBucket.WATCH;
        }
        return TrustBucket.RISKY;
    }
}
