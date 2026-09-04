package com.vyapaarmitra.api.supplier;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Page<Supplier> findByBranchIdInAndActiveTrueOrderByNameAsc(Collection<UUID> branchIds,
                                                               Pageable pageable);

    @Query("""
        select s from Supplier s
        where s.branchId in :branchIds and s.active = true
          and (lower(s.name) like lower(concat('%', :q, '%'))
               or s.phone like concat('%', :q, '%'))
        order by s.name asc
        """)
    Page<Supplier> search(@Param("branchIds") Collection<UUID> branchIds, @Param("q") String q,
                          Pageable pageable);

    /** "Highest payable first" ordering for the parties list (name breaks ties). */
    Page<Supplier> findByBranchIdInAndActiveTrueOrderByCurrentBalanceDescNameAsc(
        Collection<UUID> branchIds, Pageable pageable);

    /** Search variant ordered by highest payable (mirrors {@link #search} otherwise). */
    @Query("""
        select s from Supplier s
        where s.branchId in :branchIds and s.active = true
          and (lower(s.name) like lower(concat('%', :q, '%'))
               or s.phone like concat('%', :q, '%'))
        order by s.currentBalance desc, s.name asc
        """)
    Page<Supplier> searchByDue(@Param("branchIds") Collection<UUID> branchIds, @Param("q") String q,
                               Pageable pageable);

    @Query("""
        select coalesce(sum(s.currentBalance), 0) from Supplier s
        where s.branchId in :branchIds and s.active = true and s.currentBalance > 0
        """)
    java.math.BigDecimal totalPayable(@Param("branchIds") Collection<UUID> branchIds);

    /**
     * Party resolution for statement imports (dataimport). Matches are not restricted to
     * active parties — attaching history to an archived supplier beats creating a duplicate.
     * Callers pick deterministically from the (rarely >1) result.
     */
    List<Supplier> findByBranchIdInAndPhone(Collection<UUID> branchIds, String phone);

    /** OkCredit prints "91"-prefixed numbers; match the bare 10-digit tail of stored phones. */
    List<Supplier> findByBranchIdInAndPhoneEndingWith(Collection<UUID> branchIds, String phone);

    List<Supplier> findByBranchIdInAndNameIgnoreCase(Collection<UUID> branchIds, String name);
}
