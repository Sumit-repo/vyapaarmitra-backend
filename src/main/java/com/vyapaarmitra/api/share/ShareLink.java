package com.vyapaarmitra.api.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A public, verified share link to a customer's ledger or a bill. The 128-bit {@code token}
 * is the first factor (unguessable); the customer must then enter the party's phone last-4
 * before any data is served. Links are stable + revocable (no time expiry — see
 * docs/customer-web-viewer.md); brute-force is contained by the lockout fields.
 */
@Entity
@Table(name = "share_links")
@Getter
@Setter
@NoArgsConstructor
public class ShareLink {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareType type;

    /** Set for LEDGER shares. */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Set for BILL shares. */
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private boolean revoked = false;

    /** Wrong last-4 attempts since the last success. Drives the graduated lockout. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    /** When set and in the future, verification is blocked (cooldown / lockout). */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
