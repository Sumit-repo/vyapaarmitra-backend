package com.vyapaarmitra.api.reminder;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ReminderDtos {

    private ReminderDtos() {
    }

    public record CreateReminderRequest(@NotNull UUID customerId,
                                        UUID templateId,
                                        String channel,
                                        @NotNull ReminderOutcome outcome,
                                        LocalDate promisedDate,
                                        String note) {
    }

    public record ReminderResponse(UUID id, UUID customerId, UUID templateId, String channel,
                                   ReminderOutcome outcome, LocalDate promisedDate, String note,
                                   Instant createdAt) {

        public static ReminderResponse from(ReminderLog r) {
            return new ReminderResponse(r.getId(), r.getCustomerId(), r.getTemplateId(),
                r.getChannel(), r.getOutcome(), r.getPromisedDate(), r.getNote(), r.getCreatedAt());
        }
    }
}
