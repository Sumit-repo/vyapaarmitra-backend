package com.vyapaarmitra.api.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByBranchIdInAndActiveTrueOrderByNameAsc(Collection<UUID> branchIds,
                                                               Pageable pageable);

    /** All parties for a business — the account-export dump (bounded by the caller). */
    List<Customer> findByBusinessIdOrderByCreatedAtAsc(UUID businessId, Limit limit);

    @Query("""
        select c from Customer c
        where c.branchId in :branchIds and c.active = true
          and (lower(c.name) like lower(concat('%', :q, '%'))
               or c.phone like concat('%', :q, '%'))
        order by c.name asc
        """)
    Page<Customer> search(@Param("branchIds") Collection<UUID> branchIds, @Param("q") String q,
                          Pageable pageable);

    /** "Highest due first" ordering for the parties list (name breaks ties). */
    Page<Customer> findByBranchIdInAndActiveTrueOrderByCurrentBalanceDescNameAsc(
        Collection<UUID> branchIds, Pageable pageable);

    /** Search variant ordered by highest due (mirrors {@link #search} otherwise). */
    @Query("""
        select c from Customer c
        where c.branchId in :branchIds and c.active = true
          and (lower(c.name) like lower(concat('%', :q, '%'))
               or c.phone like concat('%', :q, '%'))
        order by c.currentBalance desc, c.name asc
        """)
    Page<Customer> searchByDue(@Param("branchIds") Collection<UUID> branchIds, @Param("q") String q,
                               Pageable pageable);

    @Query("""
        select c from Customer c
        where c.branchId in :branchIds and c.active = true
          and c.currentBalance > 0 and c.oldestDueDate <= :today
        order by c.oldestDueDate asc, c.currentBalance desc
        """)
    Page<Customer> findOverdue(@Param("branchIds") Collection<UUID> branchIds,
                               @Param("today") LocalDate today, Pageable pageable);

    @Query("""
        select coalesce(sum(c.currentBalance), 0) from Customer c
        where c.branchId in :branchIds and c.active = true and c.currentBalance > 0
        """)
    BigDecimal totalOutstanding(@Param("branchIds") Collection<UUID> branchIds);

    @Query("""
        select coalesce(sum(c.currentBalance), 0) from Customer c
        where c.branchId in :branchIds and c.active = true
          and c.currentBalance > 0 and c.oldestDueDate < :today
        """)
    BigDecimal totalOverdue(@Param("branchIds") Collection<UUID> branchIds,
                            @Param("today") LocalDate today);

    @Query("""
        select count(c) from Customer c
        where c.branchId in :branchIds and c.active = true
          and c.currentBalance > 0 and c.oldestDueDate < :today
        """)
    long countOverdue(@Param("branchIds") Collection<UUID> branchIds,
                      @Param("today") LocalDate today);

    @Query("""
        select c from Customer c
        where c.branchId in :branchIds and c.active = true and c.currentBalance > 0
        order by c.currentBalance desc
        """)
    List<Customer> topDebtors(@Param("branchIds") Collection<UUID> branchIds, Pageable pageable);

    /**
     * Count overdue customers whose oldest due date falls in [from, to) — one aging tier.
     * Bucketing on the date bound (computed in the business tz by the caller) avoids any
     * DATEDIFF/dialect ambiguity.
     */
    @Query("""
        select count(c) from Customer c
        where c.branchId in :branchIds and c.active = true and c.currentBalance > 0
          and c.oldestDueDate >= :from and c.oldestDueDate < :to
        """)
    long countOverdueByDueDateRange(@Param("branchIds") Collection<UUID> branchIds,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Count overdue customers whose oldest due date is before :before — the oldest aging tier. */
    @Query("""
        select count(c) from Customer c
        where c.branchId in :branchIds and c.active = true and c.currentBalance > 0
          and c.oldestDueDate < :before
        """)
    long countOverdueOlderThan(@Param("branchIds") Collection<UUID> branchIds,
                               @Param("before") LocalDate before);

    /** Count active customers in a trust bucket — dashboard trust distribution (PRO). */
    @Query("""
        select count(c) from Customer c
        where c.branchId in :branchIds and c.active = true and c.trustBucket = :bucket
        """)
    long countByTrustBucket(@Param("branchIds") Collection<UUID> branchIds,
                            @Param("bucket") TrustBucket bucket);

    /**
     * Party resolution for statement imports (dataimport). Matches are not restricted to
     * active parties — attaching history to an archived party beats creating a duplicate.
     * Callers pick deterministically from the (rarely >1) result.
     */
    List<Customer> findByBranchIdInAndPhone(Collection<UUID> branchIds, String phone);

    /** OkCredit prints "91"-prefixed numbers; match the bare 10-digit tail of stored phones. */
    List<Customer> findByBranchIdInAndPhoneEndingWith(Collection<UUID> branchIds, String phone);

    List<Customer> findByBranchIdInAndNameIgnoreCase(Collection<UUID> branchIds, String name);
}
