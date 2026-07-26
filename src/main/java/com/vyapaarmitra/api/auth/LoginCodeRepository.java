package com.vyapaarmitra.api.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginCodeRepository extends JpaRepository<LoginCode, UUID> {

    /** Newest usable (unconsumed) code for an email + purpose. */
    Optional<LoginCode> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
        String email, OtpPurpose purpose);

    /** Throttle window: how many codes were issued to this email since {@code since}. */
    long countByEmailAndCreatedAtAfter(String email, Instant since);

    /** Invalidate any outstanding codes for an email + purpose before issuing a new one. */
    @Modifying
    @Query("update LoginCode c set c.consumedAt = :now "
        + "where c.email = :email and c.purpose = :purpose and c.consumedAt is null")
    void consumeOutstanding(@Param("email") String email,
                            @Param("purpose") OtpPurpose purpose,
                            @Param("now") Instant now);
}
