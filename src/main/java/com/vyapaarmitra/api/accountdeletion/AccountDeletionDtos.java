package com.vyapaarmitra.api.accountdeletion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountDeletionDtos {

    private AccountDeletionDtos() {
    }

    /** Response to a deletion-OTP request — how long the emailed code stays valid. */
    public record OtpResponse(int expiresInSeconds) {
    }

    /**
     * Confirm deletion: the emailed OTP is mandatory (step-up, exactly 6 digits — matches the
     * {@code OtpService} `%06d` format); reason + feedback are optional churn capture. {@code feedback}
     * is only meaningful for {@code OTHER} but accepted for any.
     */
    public record DeletionRequest(@NotBlank @Pattern(regexp = "^\\d{6}$", message = "code must be 6 digits") String code,
                                  DeletionReason reason,
                                  @Size(max = 500) String feedback) {
    }

    /** Success payload: the ISO instant the account is scheduled to be purged. */
    public record ScheduledResponse(String scheduledAt) {
    }

    /** Reactivation success payload. */
    public record CancelResponse(String status) {
    }

    /** Status poll for banners / interstitials. */
    public record StatusResponse(boolean pendingDeletion, String scheduledAt) {
    }
}
