package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, MeResponse user) {
    }

    public record MeResponse(UUID id, String email, String fullName, Role role,
                             UUID businessId, Set<UUID> branchIds) {
    }
}
