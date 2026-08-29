package com.vyapaarmitra.api.business;

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

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
public class Business {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    // Shop GSTIN (15-char), null when not GST-registered. Passed to Razorpay so the
    // SaaS-charge invoice is GST-valid.
    @Column(name = "gstin")
    private String gstin;

    // UPI collection details — VPA (e.g. shop@bank) + the payee/merchant name shown in
    // the customer's UPI app. Null when the shop hasn't set up UPI. Owner/manager editable.
    @Column(name = "upi_vpa")
    private String upiVpa;

    @Column(name = "upi_payee_name")
    private String upiPayeeName;

    // Soft-delete marker: set alongside the owner's when they schedule account deletion,
    // cleared on reactivation. Owned businesses are purged with the owner. See
    // docs/account-deletion.md.
    @Column(name = "deletion_scheduled_at")
    private Instant deletionScheduledAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
