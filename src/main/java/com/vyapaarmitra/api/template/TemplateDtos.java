package com.vyapaarmitra.api.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record TemplateResponse(UUID id, UUID branchId, TemplateChannel channel, String category,
                                   String name, String body, boolean enabled) {

        public static TemplateResponse from(MessageTemplate t) {
            return new TemplateResponse(t.getId(), t.getBranchId(), t.getChannel(), t.getCategory(),
                t.getName(), t.getBody(), t.isEnabled());
        }
    }

    public record CreateTemplateRequest(UUID branchId,
                                        @NotNull TemplateChannel channel,
                                        @NotBlank String category,
                                        @NotBlank String name,
                                        @NotBlank String body) {
    }

    public record UpdateTemplateRequest(String category, String name, String body,
                                        Boolean enabled) {
    }

    public record RenderRequest(@NotNull UUID customerId) {
    }

    public record RenderResponse(UUID templateId, TemplateChannel channel, String text) {
    }
}
