package com.vyapaarmitra.api.subscription;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByBusinessId(UUID businessId);

    Optional<Subscription> findByGatewaySubId(String gatewaySubId);

    /**
     * Row-locked reads for the two grant paths (webhook pull, client verify). The
     * pending-plan pre-check + apply must be serialized between them or a webhook
     * landing while the buyer's client verifies can double-stack the period; both
     * grant under this lock so the second caller sees the cleared pending.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.businessId = :businessId")
    Optional<Subscription> findWithLockByBusinessId(@Param("businessId") UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.gatewaySubId = :gatewaySubId")
    Optional<Subscription> findWithLockByGatewaySubId(@Param("gatewaySubId") String gatewaySubId);

    /** Paid subs whose period ends within a window — the expiry-reminder job's daily scan. */
    List<Subscription> findByStatusAndCurrentPeriodEndBetween(SubscriptionStatus status,
                                                              Instant from, Instant to);

    /**
     * Flip trials whose window has fully lapsed to EXPIRED. Effective-plan already
     * treats a lapsed trial as FREE at read time, so this is purely for clean stored
     * status + reporting + win-back targeting. Returns the number of rows updated.
     */
    @Modifying
    @Query("update Subscription s set s.status = com.vyapaarmitra.api.subscription.SubscriptionStatus.EXPIRED, "
        + "s.updatedAt = :now "
        + "where s.status = com.vyapaarmitra.api.subscription.SubscriptionStatus.TRIALING "
        + "and s.trialEndsAt < :now")
    int expireLapsedTrials(@Param("now") Instant now);
}
