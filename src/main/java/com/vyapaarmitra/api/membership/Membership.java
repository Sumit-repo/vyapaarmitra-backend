package com.vyapaarmitra.api.membership;

import com.vyapaarmitra.api.user.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A person's role within one business. The bridge that lets a single identity
 * (see {@link com.vyapaarmitra.api.user.User}) belong to many businesses over
 * time. Owners deactivate a membership to remove someone from their shop without
 * ever touching that person's identity or their access to other shops.
 */
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    // Preferred/last-used branch within this business. Null = no preference (owner → All
    // Branches; staff → first assigned branch). Re-validated against branch access on use.
    @Column(name = "preferred_branch_id")
    private UUID preferredBranchId;

    // Branch scope for BRANCH_MANAGER / STAFF. OWNER implicitly has all branches.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "membership_branch_access", joinColumns = @JoinColumn(name = "membership_id"))
    @Column(name = "branch_id")
    private Set<UUID> branchIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
