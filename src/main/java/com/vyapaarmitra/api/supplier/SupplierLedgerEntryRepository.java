package com.vyapaarmitra.api.supplier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierLedgerEntryRepository extends JpaRepository<SupplierLedgerEntry, UUID> {

    Page<SupplierLedgerEntry> findBySupplierIdOrderByEntryAtDesc(UUID supplierId, Pageable pageable);

    List<SupplierLedgerEntry> findBySupplierIdOrderByEntryAtAsc(UUID supplierId);

    /** Signed sum (CREDIT +, PAYMENT −) of entries strictly newer than {@code after}. */
    @Query("""
        select coalesce(sum(case when e.entryType = com.vyapaarmitra.api.ledger.EntryType.CREDIT
                                 then e.amount else e.amount * -1 end), 0)
        from SupplierLedgerEntry e
        where e.supplierId = :supplierId and e.entryAt > :after
        """)
    BigDecimal signedSumAfter(@Param("supplierId") UUID supplierId, @Param("after") Instant after);
}
