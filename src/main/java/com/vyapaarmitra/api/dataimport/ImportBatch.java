package com.vyapaarmitra.api.dataimport;

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
 * One committed import (a whole statement PDF), so imported ledger rows can be
 * traced back to their batch — and excluded from plan usage.
 */
@Entity
@Table(name = "import_batches")
@Getter
@Setter
@NoArgsConstructor
public class ImportBatch {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportSource source;

    @Column(name = "parties_created", nullable = false)
    private int partiesCreated;

    @Column(name = "entries_created", nullable = false)
    private int entriesCreated;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}