package com.vyapaarmitra.api.subscription;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Housekeeping for subscription rows. The effective plan is always derived at read
 * time (a lapsed trial already reads as FREE), so this job is only about keeping the
 * <em>stored</em> status honest — it flips fully-lapsed trials to EXPIRED so reporting
 * and win-back targeting can rely on the column instead of recomputing the window.
 */
@Component
@Slf4j
public class SubscriptionMaintenance {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionMaintenance(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Daily at 00:15 IST. Time is not correctness-critical — read paths are already right. */
    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void expireLapsedTrials() {
        int expired = subscriptionRepository.expireLapsedTrials(Instant.now());
        if (expired > 0) {
            log.info("Expired {} lapsed trial subscription(s)", expired);
        }
    }
}
