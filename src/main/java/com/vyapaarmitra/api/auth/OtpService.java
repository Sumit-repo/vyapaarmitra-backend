package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.OtpRequestResponse;
import com.vyapaarmitra.api.auth.AuthDtos.OtpVerifyRequest;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.business.BusinessProvisioningService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.email.EmailSender;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email one-time-code login/signup. Codes are 6 digits, hashed at rest, single-use,
 * short-lived, attempt-capped, and throttled per email. To resist account
 * enumeration, {@link #request} always returns the same response whether or not a
 * code was actually sent (e.g. LOGIN for an unknown email, or SIGNUP for a taken one).
 */
@Slf4j
@Service
public class OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_CODES_PER_HOUR = 5;
    private static final Duration RESEND_WINDOW = Duration.ofSeconds(45);

    private final LoginCodeRepository loginCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final BusinessProvisioningService provisioningService;
    private final TokenIssuer tokenIssuer;
    private final int ttlMinutes;
    private final SecureRandom random = new SecureRandom();

    public OtpService(LoginCodeRepository loginCodeRepository, UserRepository userRepository,
                      PasswordEncoder passwordEncoder, EmailSender emailSender,
                      BusinessProvisioningService provisioningService, TokenIssuer tokenIssuer,
                      AppProperties props) {
        this.loginCodeRepository = loginCodeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.provisioningService = provisioningService;
        this.tokenIssuer = tokenIssuer;
        this.ttlMinutes = props.mail() != null && props.mail().otpTtlMinutes() > 0
            ? props.mail().otpTtlMinutes() : 10;
    }

    @Transactional
    public OtpRequestResponse request(String rawEmail, OtpPurpose purpose) {
        String email = rawEmail.trim().toLowerCase();
        int ttlSeconds = ttlMinutes * 60;

        boolean accountExists = userRepository.findByEmailIgnoreCase(email).isPresent();
        // Only send when it makes sense; otherwise respond identically (no enumeration signal).
        boolean shouldSend = purpose == OtpPurpose.LOGIN ? accountExists : !accountExists;
        if (!shouldSend) {
            return new OtpRequestResponse(ttlSeconds);
        }

        // Throttle: cap codes per email per hour, and enforce a short resend cooldown.
        Instant now = Instant.now();
        if (loginCodeRepository.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1)))
                >= MAX_CODES_PER_HOUR) {
            throw ApiException.badRequest("OTP_THROTTLED",
                "Too many codes requested. Please try again later.");
        }
        loginCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, purpose)
            .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(now.minus(RESEND_WINDOW)))
            .ifPresent(c -> {
                throw ApiException.badRequest("OTP_RESEND_TOO_SOON",
                    "Please wait a moment before requesting another code.");
            });

        loginCodeRepository.consumeOutstanding(email, purpose, now);

        String code = generateCode();
        LoginCode entity = new LoginCode();
        entity.setEmail(email);
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setPurpose(purpose);
        entity.setExpiresAt(now.plus(Duration.ofMinutes(ttlMinutes)));
        loginCodeRepository.save(entity);

        emailSender.send(email, "Your VyapaarMitra sign-in code", buildEmail(code));
        return new OtpRequestResponse(ttlSeconds);
    }

    @Transactional
    public TokenResponse verify(OtpVerifyRequest req) {
        String email = req.email().trim().toLowerCase();
        LoginCode code = loginCodeRepository
            .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, req.purpose())
            .orElseThrow(() -> ApiException.unauthorized("Invalid or expired code"));

        if (code.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Invalid or expired code");
        }
        if (code.getAttempts() >= MAX_ATTEMPTS) {
            code.setConsumedAt(Instant.now());
            throw ApiException.unauthorized("Too many attempts. Please request a new code.");
        }
        if (!passwordEncoder.matches(req.code().trim(), code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            throw ApiException.unauthorized("Invalid or expired code");
        }
        code.setConsumedAt(Instant.now());

        return req.purpose() == OtpPurpose.LOGIN ? loginExisting(email) : signup(req, email);
    }

    private TokenResponse loginExisting(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Invalid or expired code"));
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
        }
        return tokenIssuer.issue(user);
    }

    private TokenResponse signup(OtpVerifyRequest req, String email) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.badRequest("EMAIL_TAKEN", "An account with this email already exists.");
        }
        if (req.businessName() == null || req.businessName().isBlank()
            || req.ownerName() == null || req.ownerName().isBlank()) {
            throw ApiException.badRequest("SIGNUP_DETAILS_REQUIRED",
                "Business name and owner name are required to create an account.");
        }
        String branchName = req.branchName() == null || req.branchName().isBlank()
            ? "Main Branch" : req.branchName().trim();
        User owner = provisioningService.provisionOAuth(req.businessName().trim(), branchName,
            req.ownerName().trim(), email, null);
        owner.setEmailVerified(true);
        return tokenIssuer.issue(owner);
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private String buildEmail(String code) {
        return "<div style=\"font-family:sans-serif;max-width:420px\">"
            + "<h2 style=\"margin:0 0 8px\">Your sign-in code</h2>"
            + "<p style=\"color:#555\">Use this code to continue signing in to VyapaarMitra.</p>"
            + "<p style=\"font-size:32px;font-weight:700;letter-spacing:6px;margin:16px 0\">" + code + "</p>"
            + "<p style=\"color:#888;font-size:13px\">This code expires in " + ttlMinutes
            + " minutes. If you didn't request it, you can ignore this email.</p></div>";
    }
}
