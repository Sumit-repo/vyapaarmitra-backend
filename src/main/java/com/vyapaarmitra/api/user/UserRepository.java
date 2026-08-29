package com.vyapaarmitra.api.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSub(String googleSub);

    /**
     * Just the soft-delete marker for one identity — the freeze guard's per-request read.
     * Wrapped in Optional so a null marker (active account) and a missing row both read as empty.
     */
    @Query("select u.deletionScheduledAt from User u where u.id = :id")
    Optional<Instant> findDeletionScheduledAtById(@Param("id") UUID id);
}
