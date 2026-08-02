package com.vyapaarmitra.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A person (identity): their login credentials and profile. Their role and branch
 * scope in each business live on {@code memberships}, not here — one identity can
 * hold many memberships. The legacy per-business columns (business_id, role,
 * user_branch_access) were dropped in V10 once auth had fully cut over to
 * memberships.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    // The person's mobile number — a global identity key (unique via a partial
    // index in V9). Nullable at the DB for legacy rows; mandatory in the API for
    // new signups. Not yet a login credential (phone-OTP is deferred).
    @Column(name = "phone")
    private String phone;

    // Nullable: Google- or OTP-only accounts have no password.
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    // Google subject id, set once an account is linked to a Google identity.
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    // The shop this identity prefers to land on at login. Null → fall back to the most
    // recently created active membership. Re-validated against active memberships on use,
    // so a deactivated/removed business quietly falls back instead of failing.
    @Column(name = "default_business_id")
    private UUID defaultBusinessId;

    // True once this identity has consumed its single 14-day Pro trial (on the first
    // business they created). Later businesses they create start on FREE — the trial is
    // per-person, not per-business.
    @Column(name = "trial_used", nullable = false)
    private boolean trialUsed = false;

    // Consent to the cross-business defaulter network (ToS). Reciprocity: only consenting
    // identities can both contribute reports and see risk signals. See docs/defaulter-network.md.
    @Column(name = "defaulter_network_consent", nullable = false)
    private boolean defaulterNetworkConsent = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
