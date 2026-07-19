package com.vyapaarmitra.api.supplier;

import java.util.Collection;
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
}
