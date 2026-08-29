package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The freeze guard's single read: is this identity's account scheduled for deletion? Kept as a
 * thin projection over {@link UserRepository} so the per-request check is one indexed lookup and
 * the guard filter itself stays free of JPA wiring.
 */
@Component
public class PendingDeletionLookup {

    private final UserRepository userRepository;

    public PendingDeletionLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The scheduled purge instant for a user, or null when the account is active. */
    @Transactional(readOnly = true)
    public Instant deletionScheduledAt(UUID userId) {
        return userRepository.findDeletionScheduledAtById(userId).orElse(null);
    }
}
