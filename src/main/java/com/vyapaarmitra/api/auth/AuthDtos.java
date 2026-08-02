package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.subscription.PlanDtos.PlanView;
import com.vyapaarmitra.api.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    /** Ask for an email one-time code (passwordless login or verified signup). */
    public record OtpRequestRequest(@NotBlank @Email @Size(max = 190) String email,
                                    @NotNull OtpPurpose purpose) {
    }

    public record OtpRequestResponse(int expiresInSeconds) {
    }

    /**
     * Verify an email code. For SIGNUP, {@code businessName} and {@code ownerName}
     * are required (validated in the service since they don't apply to LOGIN).
     */
    public record OtpVerifyRequest(@NotBlank @Email @Size(max = 190) String email,
                                   @NotBlank @Size(min = 4, max = 8) String code,
                                   @NotNull OtpPurpose purpose,
                                   @Size(max = 120) String businessName,
                                   @Size(max = 120) String branchName,
                                   @Size(max = 120) String ownerName) {
    }

    /** Sign in with a Google ID token; businessName is only used to provision a new account. */
    public record GoogleAuthRequest(@NotBlank String idToken,
                                    @Size(max = 120) String businessName,
                                    @Size(max = 120) String branchName) {
    }

    /**
     * Either a full session, or {@code needsOnboarding=true} when a first-time
     * Google user must supply a business name before an account can be created.
     */
    public record GoogleAuthResponse(boolean needsOnboarding, String email,
                                     String suggestedName, TokenResponse session) {
    }

    public record RegisterRequest(@NotBlank @Size(max = 120) String businessName,
                                  @Size(max = 120) String branchName,
                                  @NotBlank @Size(max = 120) String ownerName,
                                  @NotBlank @Email @Size(max = 190) String email,
                                  @NotBlank @Size(min = 7, max = 20) String phone,
                                  @NotBlank @Size(min = 8, max = 100) String password,
                                  // Opt-in to the defaulter network at signup (ToS). Null → false.
                                  Boolean defaulterNetworkConsent) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Switch the active business for an already-authenticated identity. */
    public record SelectBusinessRequest(@NotNull UUID businessId) {
    }

    /** Pin the identity's default shop (the one login lands on). */
    public record SetDefaultBusinessRequest(@NotNull UUID businessId) {
    }

    /** Set the acting membership's preferred branch; null clears it (no preference). */
    public record SetPreferredBranchRequest(UUID branchId) {
    }

    public record TokenResponse(String accessToken, String refreshToken, MeResponse user) {
    }

    public record MeResponse(UUID id, String email, String fullName, String businessName,
                             Role role, UUID businessId, Set<UUID> branchIds,
                             UUID defaultBusinessId, UUID preferredBranchId, PlanView plan) {
    }

    /** One business an identity can act in — powers the business switcher. */
    public record BusinessMembershipView(UUID businessId, String businessName, Role role) {
    }
}
