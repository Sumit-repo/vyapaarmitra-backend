package com.vyapaarmitra.api.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    /** Active memberships for an identity — the businesses a person can sign into. */
    List<Membership> findByUserIdAndActiveTrue(UUID userId);

    /** A specific person's membership in a specific business (any status). */
    Optional<Membership> findByUserIdAndBusinessId(UUID userId, UUID businessId);

    /** Everyone attached to a business — the staff roster (any status). */
    List<Membership> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);
}
