package com.vyapaarmitra.api.subscription;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByBusinessId(UUID businessId);

    Optional<Subscription> findByGatewaySubId(String gatewaySubId);

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
