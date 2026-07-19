package com.vyapaarmitra.api.reminder;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ReminderSettingsDtos {

    private ReminderSettingsDtos() {
    }

    public record UpdateReminderSettingsRequest(
        Boolean smsReminderEnabled,
        @Pattern(regexp = "SMS|WHATSAPP", message = "must be SMS or WHATSAPP")
        String preferredChannel,
        UUID reminderTemplateId,
        Instant nextReminderDueAt,
        Boolean autoScheduleEnabled,
        @Size(max = 500) String reminderNotes
    ) {
    }

    public record ReminderSettingsResponse(
        UUID customerId,
        boolean smsReminderEnabled,
        String preferredChannel,
        UUID reminderTemplateId,
        Instant lastReminderPromptedAt,
        Instant lastReminderSentAt,
        String lastReminderType,
        Instant nextReminderDueAt,
        boolean autoScheduleEnabled,
        String reminderNotes
    ) {

        public static ReminderSettingsResponse from(CustomerReminderSettings s) {
            return new ReminderSettingsResponse(
                s.getCustomerId(), s.isSmsReminderEnabled(), s.getPreferredChannel(),
                s.getReminderTemplateId(), s.getLastReminderPromptedAt(), s.getLastReminderSentAt(),
                s.getLastReminderType(), s.getNextReminderDueAt(), s.isAutoScheduleEnabled(),
                s.getReminderNotes()
            );
        }
    }

    /** Returned by GET /customers/{id}/reminder-message — prefill this into the Android SMS intent. */
    public record ReminderMessageResponse(
        UUID customerId,
        String phone,
        String message,
        UUID templateId,
        String channel
    ) {
    }

    /** One item in the GET /reminders/due list shown in the merchant's reminder center. */
    public record DueReminderItem(
        UUID customerId,
        String customerName,
        String phone,
        BigDecimal currentBalance,
        LocalDate oldestDueDate,
        int overdueDays,
        String preferredChannel,
        Instant nextReminderDueAt,
        Instant lastReminderSentAt
    ) {
    }

    /** POST /reminders/customers/{id}/prompted — optionally reschedule next prompt. */
    public record PromptedRequest(Instant nextReminderDueAt) {
    }

    /** POST /reminders/customers/{id}/sent — records the SMS was handed off to the OS composer. */
    public record SentRequest(
        @Size(max = 50) String type,
        UUID templateId,
        @Size(max = 500) String note
    ) {
    }
}
