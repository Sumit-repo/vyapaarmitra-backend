package com.vyapaarmitra.api.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class BranchDtos {

    private BranchDtos() {
    }

    public record BranchResponse(UUID id, String name, String address, boolean active,
                                 Instant deactivatedAt) {

        public static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress(),
                branch.isActive(), branch.getDeactivatedAt());
        }
    }

    public record CreateBranchRequest(@NotBlank @Size(max = 100) String name,
                                      @Size(max = 300) String address) {
    }

    public record UpdateBranchRequest(@Size(max = 100) String name,
                                      @Size(max = 300) String address, Boolean active) {
    }
}
