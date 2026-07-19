package com.vyapaarmitra.api.business;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    @Query("select b.id from Branch b where b.businessId = :businessId and b.active = true")
    Set<UUID> findActiveIdsByBusinessId(@Param("businessId") UUID businessId);
}
