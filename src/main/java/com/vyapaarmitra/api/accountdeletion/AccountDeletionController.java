package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.CancelResponse;
import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.DeletionRequest;
import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.OtpResponse;
import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.ScheduledResponse;
import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.StatusResponse;
import com.vyapaarmitra.api.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account deletion. All endpoints act on the authenticated identity (never a
 * body-supplied user id). The cancel/status routes stay reachable during the soft-delete grace
 * via the freeze-guard allowlist. See docs/account-deletion.md.
 */
@RestController
@RequestMapping("/api/v1/account/deletion")
public class AccountDeletionController {

    private final AccountDeletionService service;

    public AccountDeletionController(AccountDeletionService service) {
        this.service = service;
    }

    /** Send a step-up OTP to the authed user's own email. */
    @PostMapping("/otp")
    public OtpResponse requestOtp(@AuthenticationPrincipal AuthUser authUser) {
        return service.requestOtp(authUser.id());
    }

    /** Verify the OTP and soft-delete (schedule) the account + owned businesses. */
    @PostMapping
    public ScheduledResponse confirm(@AuthenticationPrincipal AuthUser authUser,
                                     @Valid @RequestBody DeletionRequest request) {
        return service.confirm(authUser.id(), request.reason(), request.feedback(), request.code());
    }

    /** Reactivate — clear the schedule. One tap, no OTP. Allowed during grace. */
    @PostMapping("/cancel")
    public CancelResponse cancel(@AuthenticationPrincipal AuthUser authUser) {
        service.cancel(authUser.id());
        return new CancelResponse("active");
    }

    /** Status poll for banners / interstitials. Allowed during grace. */
    @GetMapping
    public StatusResponse status(@AuthenticationPrincipal AuthUser authUser) {
        return service.status(authUser.id());
    }
}
