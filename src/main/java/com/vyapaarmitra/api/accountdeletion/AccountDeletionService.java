package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.OtpResponse;
import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.ScheduledResponse;
import com.vyapaarmitra.api.accountdeletion.AccountDeletionDtos.StatusResponse;
import com.vyapaarmitra.api.auth.OtpPurpose;
import com.vyapaarmitra.api.auth.OtpService;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.email.EmailSender;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service account deletion: request an OTP, confirm (soft-delete + freeze), and reactivate.
 * Soft delete marks the identity and the businesses it OWNS with {@code deletion_scheduled_at =
 * now + GRACE_DAYS}; a request guard freezes normal API calls during the grace window; the purge
 * worker hard-deletes once it lapses. Reactivation is one tap (no OTP) — the safe/reversible
 * direction. Package-by-feature; see docs/account-deletion.md.
 */
@Slf4j
@Service
public class AccountDeletionService {

    /** The soft-delete grace window: nothing is destroyed until it lapses. */
    static final int GRACE_DAYS = 30;

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final MembershipRepository membershipRepository;
    private final AccountDeletionRequestRepository requestRepository;
    private final EmailSender emailSender;

    public AccountDeletionService(OtpService otpService, UserRepository userRepository,
                                  BusinessRepository businessRepository,
                                  MembershipRepository membershipRepository,
                                  AccountDeletionRequestRepository requestRepository,
                                  EmailSender emailSender) {
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.requestRepository = requestRepository;
        this.emailSender = emailSender;
    }

    /** Email the caller a step-up code for account deletion. Rate-limited by {@link OtpService}. */
    @Transactional
    public OtpResponse requestOtp(UUID userId) {
        User user = requireUser(userId);
        return new OtpResponse(otpService.request(user.getEmail(), OtpPurpose.ACCOUNT_DELETION)
            .expiresInSeconds());
    }

    /**
     * Confirm deletion: verify the OTP, record the request (audit + churn), set the soft-delete
     * marker on the identity and every business it owns, and email a confirmation. Idempotent for
     * a repeat submit while already pending — returns the existing schedule without a second OTP
     * hit is NOT done here (OTP is single-use); a second valid code simply reschedules, which is
     * harmless. 401 on an invalid/expired code.
     */
    @Transactional
    public ScheduledResponse confirm(UUID userId, DeletionReason reason, String feedback,
                                     String code) {
        User user = requireUser(userId);
        // Step-up: single source of truth for OTP validation (see OtpService.verifyCode).
        otpService.verifyCode(user.getEmail(), code, OtpPurpose.ACCOUNT_DELETION);

        Instant now = Instant.now();
        Instant scheduledAt = now.plus(GRACE_DAYS, ChronoUnit.DAYS);

        user.setDeletionScheduledAt(scheduledAt);
        userRepository.save(user);

        List<Business> owned = ownedBusinesses(userId);
        for (Business business : owned) {
            business.setDeletionScheduledAt(scheduledAt);
        }
        businessRepository.saveAll(owned);

        AccountDeletionRequest request = requestRepository
            .findFirstByUserIdAndCancelledAtIsNullAndCompletedAtIsNull(userId)
            .orElseGet(AccountDeletionRequest::new);
        request.setUserId(userId);
        request.setReason(reason);
        request.setFeedback(feedback == null || feedback.isBlank() ? null : feedback.trim());
        request.setScheduledAt(scheduledAt);
        requestRepository.save(request);

        sendConfirmationEmail(user.getEmail(), scheduledAt);
        log.info("Account deletion scheduled for user {} at {} ({} owned business(es))",
            userId, scheduledAt, owned.size());
        return new ScheduledResponse(scheduledAt.toString());
    }

    /** Reactivation: clear the soft-delete marker on the identity + owned businesses. One tap. */
    @Transactional
    public void cancel(UUID userId) {
        User user = requireUser(userId);
        if (user.getDeletionScheduledAt() == null) {
            // Already active — reactivation is idempotent; nothing to undo.
            return;
        }
        user.setDeletionScheduledAt(null);
        userRepository.save(user);

        List<Business> owned = ownedBusinesses(userId);
        for (Business business : owned) {
            business.setDeletionScheduledAt(null);
        }
        businessRepository.saveAll(owned);

        Instant now = Instant.now();
        requestRepository.findFirstByUserIdAndCancelledAtIsNullAndCompletedAtIsNull(userId)
            .ifPresent(r -> {
                r.setCancelledAt(now);
                requestRepository.save(r);
            });
        log.info("Account deletion cancelled (reactivated) for user {}", userId);
    }

    /** Status poll for banners / interstitials. */
    @Transactional(readOnly = true)
    public StatusResponse status(UUID userId) {
        Instant scheduledAt = requireUser(userId).getDeletionScheduledAt();
        return new StatusResponse(scheduledAt != null,
            scheduledAt != null ? scheduledAt.toString() : null);
    }

    /** Businesses where this identity holds an OWNER membership. */
    private List<Business> ownedBusinesses(UUID userId) {
        List<UUID> ownedBusinessIds = membershipRepository.findByUserIdAndActiveTrue(userId).stream()
            .filter(m -> m.getRole() == Role.OWNER)
            .map(Membership::getBusinessId)
            .toList();
        return businessRepository.findAllById(ownedBusinessIds);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }

    private void sendConfirmationEmail(String email, Instant scheduledAt) {
        String date = DATE_FMT.format(scheduledAt);
        String body = "<div style=\"font-family:sans-serif;max-width:460px\">"
            + "<h2 style=\"margin:0 0 8px\">Your account is scheduled for deletion</h2>"
            + "<p style=\"color:#555\">Nothing is deleted yet. Your VyapaarMitra account and its"
            + " khata are safe until <strong>" + date + "</strong>.</p>"
            + "<p style=\"color:#555\">Changed your mind? Open the app and tap"
            + " <strong>Reactivate my account</strong> any time before then — everything stays"
            + " exactly as you left it.</p>"
            + "<p style=\"color:#888;font-size:13px\">If you didn't request this, reactivate now"
            + " and consider changing how you sign in.</p></div>";
        try {
            emailSender.send(email, "Your VyapaarMitra account is scheduled for deletion", body);
        } catch (RuntimeException e) {
            // Best-effort: the schedule is already committed; a failed email must not roll it back.
            log.warn("Failed to send deletion confirmation email to {}: {}", email, e.getMessage());
        }
    }
}
