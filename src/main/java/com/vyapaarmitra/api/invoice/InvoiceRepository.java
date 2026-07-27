package com.vyapaarmitra.api.invoice;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByBranchIdInOrderByCreatedAtDesc(Collection<UUID> branchIds, Pageable pageable);

    Page<Invoice> findByBranchIdInAndBillTypeOrderByCreatedAtDesc(Collection<UUID> branchIds,
                                                                  BillType billType, Pageable pageable);

    @Query("""
        select i from Invoice i
        where i.branchId in :branchIds
          and (lower(i.number) like lower(concat('%', :q, '%'))
               or lower(i.partyName) like lower(concat('%', :q, '%'))
               or i.partyPhone like concat('%', :q, '%'))
        order by i.createdAt desc
        """)
    Page<Invoice> search(@Param("branchIds") Collection<UUID> branchIds, @Param("q") String q,
                         Pageable pageable);

    /** Count of a branch's bills of a given type — drives the number series. */
    long countByBranchIdAndBillType(UUID branchId, BillType billType);

    /** Bills a business raised in a half-open window — drives daily-entry usage. */
    @Query("""
        select count(i) from Invoice i
        where i.businessId = :businessId and i.createdAt >= :from and i.createdAt < :to
        """)
    long countCreatedBetween(@Param("businessId") UUID businessId,
                             @Param("from") Instant from, @Param("to") Instant to);

    /** Pakka (GST) bills a business raised in a half-open window — drives the monthly GST cap. */
    @Query("""
        select count(i) from Invoice i
        where i.businessId = :businessId and i.billType = :type
          and i.createdAt >= :from and i.createdAt < :to
        """)
    long countByTypeCreatedBetween(@Param("businessId") UUID businessId, @Param("type") BillType type,
                                   @Param("from") Instant from, @Param("to") Instant to);
}
