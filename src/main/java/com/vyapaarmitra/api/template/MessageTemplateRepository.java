package com.vyapaarmitra.api.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    @Query("""
        select t from MessageTemplate t
        where t.businessId = :businessId
          and (t.branchId is null or t.branchId = :branchId)
        order by t.category asc, t.name asc
        """)
    List<MessageTemplate> findForBranch(@Param("businessId") UUID businessId,
                                        @Param("branchId") UUID branchId);

    List<MessageTemplate> findByBusinessIdOrderByCategoryAscNameAsc(UUID businessId);
}
