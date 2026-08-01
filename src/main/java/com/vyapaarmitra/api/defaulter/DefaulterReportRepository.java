package com.vyapaarmitra.api.defaulter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefaulterReportRepository extends JpaRepository<DefaulterReport, UUID> {

    /** A shop's existing report for a phone (upsert target for the warn action). */
    Optional<DefaulterReport> findByNormalizedPhoneAndBusinessId(String normalizedPhone, UUID businessId);

    /** Live reports for a customer — pay-to-clear targets. */
    List<DefaulterReport> findByCustomerIdAndStatusIn(UUID customerId, Collection<DefaulterStatus> statuses);

    /** Warnings whose grace may have lapsed — the activation job's candidates. */
    List<DefaulterReport> findByStatusAndWarningSentAtLessThanEqual(DefaulterStatus status, Instant cutoff);

    /** Network signal: is this phone actively flagged by anyone? (Phase B lookup.) */
    boolean existsByNormalizedPhoneAndStatus(String normalizedPhone, DefaulterStatus status);
}
