package com.vyapaarmitra.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(UUID id, String email, String fullName, Role role, boolean active,
                               Set<UUID> branchIds) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.isActive(), Set.copyOf(user.getBranchIds()));
        }
    }

    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Size(min = 8, max = 72) String password,
                                    @NotBlank @Size(max = 100) String fullName,
                                    @NotNull Role role,
                                    Set<UUID> branchIds) {
    }

    public record UpdateUserRequest(@Size(max = 100) String fullName, Role role, Boolean active,
                                    Set<UUID> branchIds,
                                    @Size(min = 8, max = 72) String password) {
    }
}
