package com.vyapaarmitra.api.reminder;

import com.vyapaarmitra.api.customer.Customer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerReminderSettingsRepository
    extends JpaRepository<CustomerReminderSettings, UUID> {

    /**
     * Returns reminder settings (in reminder-priority order) for customers
     * that are due for an SMS prompt. A customer is "due" when:
     *   - sms_reminder_enabled = true
     *   - next_reminder_due_at has passed, OR they have an overdue balance today
     *
     * The companion Customer entity is accessed via theta-join so the service
     * can batch-load them in a single follow-up query.
     */
    @Query("""
        SELECT s FROM CustomerReminderSettings s, Customer c
        WHERE s.customerId = c.id
        AND c.businessId = :businessId
        AND s.branchId IN :branchIds
        AND c.active = true
        AND s.smsReminderEnabled = true
        AND (s.nextReminderDueAt <= :now
             OR (c.currentBalance > 0
                 AND c.oldestDueDate IS NOT NULL
                 AND c.oldestDueDate <= :today))
        ORDER BY c.oldestDueDate ASC NULLS LAST, c.name ASC
        """)
    List<CustomerReminderSettings> findDue(
        @Param("businessId") UUID businessId,
        @Param("branchIds") Collection<UUID> branchIds,
        @Param("now") Instant now,
        @Param("today") LocalDate today,
        Pageable pageable
    );
}
