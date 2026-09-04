package com.vyapaarmitra.api.billlayout;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * The shop's chosen bill design. At most one row per business (unique
 * {@code business_id}); an absent row is the read-only CLASSIC default and is never
 * seeded. The shop logo itself lives on {@code businesses} (logo_url / logo_public_id).
 */
@Entity
@Table(name = "bill_layouts")
@Getter
@Setter
@NoArgsConstructor
public class BillLayout {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillPreset layout = BillPreset.CLASSIC;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private BillLayoutOptions options = BillLayoutOptions.defaults();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}