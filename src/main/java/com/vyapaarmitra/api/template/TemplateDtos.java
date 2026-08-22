package com.vyapaarmitra.api.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    public record RenderRequest(@NotNull UUID customerId) {
    }

    public record RenderResponse(UUID templateId, String text) {
    }
}
