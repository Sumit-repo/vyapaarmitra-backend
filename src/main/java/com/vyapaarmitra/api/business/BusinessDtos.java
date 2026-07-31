package com.vyapaarmitra.api.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record BusinessResponse(UUID id, String name) {
        public static BusinessResponse from(Business business) {
            return new BusinessResponse(business.getId(), business.getName());
        }
    }

    /** Rename the shop. Owner-only (enforced at the controller). */
    public record UpdateBusinessRequest(@NotBlank @Size(max = 120) String name) {
    }
}
