package com.vyapaarmitra.api.defaulter;

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
 * One merchant's report against a customer phone number on the defaulter network. Keyed by
 * the normalized phone (the network key); {@code customerId} links it to the reporting shop's
 * ledger so a settling payment can auto-clear it. See web docs/defaulter-network.md.
 */
@Entity
@Table(name = "defaulter_reports")
@Getter
@Setter
@NoArgsConstructor
public class DefaulterReport {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "normalized_phone", nullable = false)
    private String normalizedPhone;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "reported_by_user_id", nullable = false)
    private UUID reportedByUserId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefaulterStatus status;

    // Snapshot of how overdue the debt was when reported (>= 90) — the verifiable trail.
    @Column(name = "overdue_days_at_report", nullable = false)
    private int overdueDaysAtReport;

    @Column(name = "warning_sent_at", nullable = false)
    private Instant warningSentAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
