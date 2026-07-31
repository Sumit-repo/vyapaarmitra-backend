package com.vyapaarmitra.api.user;

import com.vyapaarmitra.api.membership.Membership;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    /**
     * A staff member as seen on the roster: identity fields (email/phone/name) come
     * from the person, while role/active/branch scope come from their membership in
     * this business. {@code id} is the identity id — the key the roster acts on.
     */
    public record UserResponse(UUID id, String email, String phone, String fullName, Role role,
                               boolean active, Set<UUID> branchIds) {

        public static UserResponse from(User user, Membership membership) {
            return new UserResponse(user.getId(), user.getEmail(), user.getPhone(), user.getFullName(),
                membership.getRole(), membership.isActive(), Set.copyOf(membership.getBranchIds()));
        }
    }

    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Size(min = 7, max = 20) String phone,
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
