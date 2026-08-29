package com.vyapaarmitra.api.accountdeletion;

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
import org.hibernate.annotations.UuidGenerator;

/**
 * A record of one account-deletion request: the audit trail, the churn dataset, and who
 * to contact during the 30-day grace. One "open" row per identity at a time (partial index
 * where cancelled_at is null and completed_at is null). Cancelled on reactivation; completed
 * when purge hard-deletes. See docs/account-deletion.md.
 */
@Entity
@Table(name = "account_deletion_requests")
@Getter
@Setter
@NoArgsConstructor
public class AccountDeletionRequest {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    private DeletionReason reason;

    @Column(name = "feedback")
    private String feedback;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private Instant requestedAt;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
