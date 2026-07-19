package com.vyapaarmitra.api.reminder;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, UUID> {

    Page<ReminderLog> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<ReminderLog> findByBranchIdInOrderByCreatedAtDesc(Collection<UUID> branchIds,
                                                           Pageable pageable);
}
