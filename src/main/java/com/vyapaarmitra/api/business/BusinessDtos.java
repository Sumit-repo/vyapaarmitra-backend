package com.vyapaarmitra.api.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record BusinessResponse(UUID id, String name, String gstin,
                                   String upiVpa, String upiPayeeName) {
        public static BusinessResponse from(Business business) {
            return new BusinessResponse(business.getId(), business.getName(), business.getGstin(),
                business.getUpiVpa(), business.getUpiPayeeName());
        }
    }

    /**
     * Set/clear the shop's UPI collection details. Owner or branch manager (enforced at
     * the controller). A blank {@code upiVpa} clears both fields. VPA format is validated
     * in the service; {@code upiPayeeName} is the name shown in the customer's UPI app.
     */
    public record UpdateUpiRequest(@Size(max = 256) String upiVpa,
                                   @Size(max = 120) String upiPayeeName) {
    }

    /**
     * Update shop profile (name + optional GSTIN). Owner-only (enforced at the
     * controller). {@code name} stays required (rename semantics); a blank {@code gstin}
     * clears it.
     */
    public record UpdateBusinessRequest(@NotBlank @Size(max = 120) String name,
                                        @Size(max = 20) String gstin) {
    }

    /**
     * Create another business for the signed-in identity. They become its OWNER; the
     * subscription trials only if they haven't used their one trial. {@code branchName}
     * is optional (defaults to a first branch).
     */
    public record CreateBusinessRequest(@NotBlank @Size(max = 120) String name,
                                        @Size(max = 120) String branchName) {
    }
}
