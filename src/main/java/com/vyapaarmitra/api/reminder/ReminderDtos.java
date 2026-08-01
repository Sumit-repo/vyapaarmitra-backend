package com.vyapaarmitra.api.reminder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ReminderDtos {

    private ReminderDtos() {
    }

    public record CreateReminderRequest(@NotNull UUID customerId,
                                        UUID templateId,
                                        @Pattern(regexp = "SMS|WHATSAPP|CALL",
                                            message = "must be SMS, WHATSAPP or CALL")
                                        String channel,
                                        @NotNull ReminderOutcome outcome,
                                        LocalDate promisedDate,
                                        @Size(max = 1000) String note) {
    }

    public record ReminderResponse(UUID id, UUID customerId, UUID templateId, String channel,
                                   ReminderOutcome outcome, LocalDate promisedDate, String note,
                                   Instant createdAt, UUID createdBy, String createdByName) {

        /** {@code createdByName} is the resolved name of whoever logged the reminder (business-local). */
        public static ReminderResponse from(ReminderLog r, String createdByName) {
            return new ReminderResponse(r.getId(), r.getCustomerId(), r.getTemplateId(),
                r.getChannel(), r.getOutcome(), r.getPromisedDate(), r.getNote(), r.getCreatedAt(),
                r.getCreatedBy(), createdByName);
        }
    }
}
