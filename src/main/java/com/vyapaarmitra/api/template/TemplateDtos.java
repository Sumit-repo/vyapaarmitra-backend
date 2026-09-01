package com.vyapaarmitra.api.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record TemplateResponse(UUID id, UUID branchId, String category,
                                   String name, String body, boolean enabled) {

        public static TemplateResponse from(MessageTemplate t) {
            return new TemplateResponse(t.getId(), t.getBranchId(), t.getCategory(),
                t.getName(), t.getBody(), t.isEnabled());
        }
    }

    public record CreateTemplateRequest(UUID branchId,
                                        @NotBlank @Size(max = 50) String category,
                                        @NotBlank @Size(max = 100) String name,
                                        @NotBlank @Size(max = 1000) String body) {
    }

    public record UpdateTemplateRequest(@Size(max = 50) String category,
                                        @Size(max = 100) String name,
                                        @Size(max = 1000) String body,
                                        Boolean enabled) {
    }

    /** Optional settlement window: when both dates are present the renderer also fills the
     *  window_* tokens (period dates, credit/payment sums, balance at window close). */
    public record RenderRequest(@NotNull UUID customerId,
                                LocalDate startDate,
                                LocalDate endDate) {
    }

    public record RenderResponse(UUID templateId, String text) {
    }
}
