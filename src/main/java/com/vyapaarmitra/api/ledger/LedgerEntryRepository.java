package com.vyapaarmitra.api.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByCustomerIdOrderByEntryAtDesc(UUID customerId, Pageable pageable);

    /** All ledger entries for a business — the account-export dump (bounded by the caller). */
    List<LedgerEntry> findByBusinessIdOrderByEntryAtAsc(UUID businessId, Limit limit);

    List<LedgerEntry> findByCustomerIdOrderByEntryAtAsc(UUID customerId);

    /**
     * Signed sum (CREDIT +, PAYMENT −) of entries strictly newer than {@code after}.
     * Used to anchor a page's running balance without replaying the whole ledger:
     * balanceAfter(newest-on-page) = currentBalance − signedSumAfter(that entry's instant).
     */
    @Query("""
        select coalesce(sum(case when e.entryType = com.vyapaarmitra.api.ledger.EntryType.CREDIT
                                 then e.amount else e.amount * -1 end), 0)
        from LedgerEntry e
        where e.customerId = :customerId and e.entryAt > :after
        """)
    BigDecimal signedSumAfter(@Param("customerId") UUID customerId, @Param("after") Instant after);

    /** Sum of one entry type for a single customer in a half-open window [from, to). */
    @Query("""
        select coalesce(sum(e.amount), 0) from LedgerEntry e
        where e.customerId = :customerId and e.entryType = :type
          and e.entryAt >= :from and e.entryAt < :to
        """)
    BigDecimal sumByCustomerAndTypeBetween(@Param("customerId") UUID customerId,
                                           @Param("type") EntryType type,
                                           @Param("from") Instant from, @Param("to") Instant to);

    /** Entries for one customer inside a half-open window — import duplicate detection. */
    List<LedgerEntry> findByCustomerIdAndEntryAtGreaterThanEqualAndEntryAtLessThan(
        UUID customerId, Instant from, Instant to);

    /** Shop-wide statement feed: all entries for the scoped branches since `from`, oldest first. */
    List<LedgerEntry> findByBranchIdInAndEntryAtGreaterThanEqualOrderByEntryAtAsc(
        Collection<UUID> branchIds, Instant from);

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

    /**
     * Business-scoped entry count in a half-open window — drives daily-entry usage.
     * Imported rows are excluded: a one-time statement backfill must not burn the cap.
     */
    @Query("""
        select count(e) from LedgerEntry e
        where e.businessId = :businessId and e.entryAt >= :from and e.entryAt < :to
          and e.importBatchId is null
        """)
    long countByBusinessBetweenExcludingImports(@Param("businessId") UUID businessId,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);
}
