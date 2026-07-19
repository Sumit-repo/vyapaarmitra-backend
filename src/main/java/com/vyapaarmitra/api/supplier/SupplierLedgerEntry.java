package com.vyapaarmitra.api.supplier;

import com.vyapaarmitra.api.ledger.EntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "supplier_ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class SupplierLedgerEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    private String method;

    private String note;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "entry_at", nullable = false)
    private Instant entryAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
