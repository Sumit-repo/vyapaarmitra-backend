package com.vyapaarmitra.api.supplier;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierLedgerEntryRepository extends JpaRepository<SupplierLedgerEntry, UUID> {

    Page<SupplierLedgerEntry> findBySupplierIdOrderByEntryAtDesc(UUID supplierId, Pageable pageable);

    List<SupplierLedgerEntry> findBySupplierIdOrderByEntryAtAsc(UUID supplierId);
}
