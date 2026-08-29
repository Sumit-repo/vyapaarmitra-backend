package com.vyapaarmitra.api.accountdeletion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionRequestRepository
    extends JpaRepository<AccountDeletionRequest, UUID> {

    /** The single open (not cancelled, not completed) request for an identity, if any. */
    Optional<AccountDeletionRequest> findFirstByUserIdAndCancelledAtIsNullAndCompletedAtIsNull(
        UUID userId);

    /** Open requests whose grace has lapsed — the purge worklist. Bounded per sweep. */
    List<AccountDeletionRequest> findByCancelledAtIsNullAndCompletedAtIsNullAndScheduledAtLessThan(
        Instant now, Limit limit);

    /**
     * Open requests scheduled to complete within a [from, to) window — the daily T-3-day reminder
     * pass. Running once/day with a 24h window fires the nudge exactly once per request without a
     * dedicated "sent" flag. Bounded per pass.
     */
    List<AccountDeletionRequest>
        findByCancelledAtIsNullAndCompletedAtIsNullAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            Instant from, Instant to, Limit limit);
}
