package com.vyapaarmitra.api.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "customer_reminder_settings")
@Getter
@Setter
@NoArgsConstructor
public class CustomerReminderSettings {

    /** Same UUID as the customer — this is the PK, not a generated surrogate. */
    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "sms_reminder_enabled", nullable = false)
    private boolean smsReminderEnabled = false;

    @Column(name = "preferred_channel", nullable = false)
    private String preferredChannel = "SMS";

    @Column(name = "reminder_template_id")
    private UUID reminderTemplateId;

    @Column(name = "last_reminder_prompted_at")
    private Instant lastReminderPromptedAt;

    @Column(name = "last_reminder_sent_at")
    private Instant lastReminderSentAt;

    @Column(name = "last_reminder_type")
    private String lastReminderType;

    @Column(name = "next_reminder_due_at")
    private Instant nextReminderDueAt;

    @Column(name = "auto_schedule_enabled", nullable = false)
    private boolean autoScheduleEnabled = false;

    @Column(name = "reminder_notes")
    private String reminderNotes;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
