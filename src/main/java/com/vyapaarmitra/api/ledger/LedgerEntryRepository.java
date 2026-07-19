package com.vyapaarmitra.api.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByCustomerIdOrderByEntryAtDesc(UUID customerId, Pageable pageable);

    List<LedgerEntry> findByCustomerIdOrderByEntryAtAsc(UUID customerId);

    @Query("""
        select coalesce(sum(e.amount), 0) from LedgerEntry e
        where e.branchId in :branchIds and e.entryType = :type
          and e.entryAt >= :from and e.entryAt < :to
        """)
    BigDecimal sumByTypeBetween(@Param("branchIds") Collection<UUID> branchIds,
                                @Param("type") EntryType type,
                                @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        select count(e) from LedgerEntry e
        where e.branchId in :branchIds and e.entryAt >= :from and e.entryAt < :to
        """)
    long countBetween(@Param("branchIds") Collection<UUID> branchIds,
                      @Param("from") Instant from, @Param("to") Instant to);
}
