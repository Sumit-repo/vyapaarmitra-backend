package com.vyapaarmitra.api.business;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class BranchDtos {

    private BranchDtos() {
    }

    public record BranchResponse(UUID id, String name, String address, boolean active) {

        public static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress(),
                branch.isActive());
        }
    }

    public record CreateBranchRequest(@NotBlank String name, String address) {
    }

    public record UpdateBranchRequest(String name, String address, Boolean active) {
    }
}
