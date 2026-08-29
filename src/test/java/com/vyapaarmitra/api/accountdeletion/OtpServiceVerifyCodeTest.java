package com.vyapaarmitra.api.accountdeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthDtos.OtpVerifyRequest;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.auth.LoginCode;
import com.vyapaarmitra.api.auth.LoginCodeRepository;
import com.vyapaarmitra.api.auth.OtpPurpose;
import com.vyapaarmitra.api.auth.OtpService;
import com.vyapaarmitra.api.business.BusinessProvisioningService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.email.EmailSender;
import com.vyapaarmitra.api.membership.MembershipService;
import com.vyapaarmitra.api.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * §11 rows 2-4: the OTP layer under account deletion. {@code verifyCode} is the single source of
 * truth extracted from {@code verify()} so login, signup, and account-deletion step-up never drift;
 * these cover the wrong-code / expired / max-attempts paths that every caller inherits.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceVerifyCodeTest {

    @Mock private LoginCodeRepository loginCodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailSender emailSender;
    @Mock private BusinessProvisioningService provisioningService;
    @Mock private MembershipService membershipService;
    @Mock private com.vyapaarmitra.api.auth.TokenIssuer tokenIssuer;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        AppProperties.Mail mail = new AppProperties.Mail("from", null, 10);
        AppProperties props = new AppProperties("Asia/Kolkata", "http://localhost:3000",
            new AppProperties.Jwt("secret", 30, 30),
            new AppProperties.Cors(java.util.List.of("http://localhost:3000")),
            new AppProperties.Bootstrap(null, null, "Owner", "Shop", "Main"),
            new AppProperties.Google(java.util.List.of()),
            mail, null);
        otpService = new OtpService(loginCodeRepository, userRepository, passwordEncoder,
            emailSender, provisioningService, membershipService, tokenIssuer, props);
    }

    private LoginCode outstandingCode() {
        LoginCode code = new LoginCode();
        code.setEmail("owner@shop.com");
        code.setPurpose(OtpPurpose.ACCOUNT_DELETION);
        code.setExpiresAt(Instant.now().plus(5, java.time.temporal.ChronoUnit.MINUTES));
        code.setAttempts(0);
        code.setCodeHash("hashed");
        return code;
    }

    // §11: wrong OTP → 401; attempt counter increments (the code row is mutated, not consumed).
    @Test
    void verifyCodeWithWrongCodeIncrementsAttemptsAndThrows() {
        LoginCode code = outstandingCode();
        when(loginCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "owner@shop.com", OtpPurpose.ACCOUNT_DELETION))
            .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("000000", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> otpService.verifyCode("Owner@Shop.com", "000000",
                OtpPurpose.ACCOUNT_DELETION))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Invalid or expired code");

        assertThat(code.getAttempts()).isEqualTo(1); // incremented
        assertThat(code.getConsumedAt()).isNull();    // NOT consumed
    }

    // §11: expired OTP → 401; no state change (no attempt increment, not consumed).
    @Test
    void verifyCodeWithExpiredCodeThrowsWithoutStateChange() {
        LoginCode code = outstandingCode();
        code.setExpiresAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.MINUTES));
        when(loginCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "owner@shop.com", OtpPurpose.ACCOUNT_DELETION))
            .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> otpService.verifyCode("owner@shop.com", "123456",
                OtpPurpose.ACCOUNT_DELETION))
            .isInstanceOf(ApiException.class);

        assertThat(code.getAttempts()).isZero();
        assertThat(code.getConsumedAt()).isNull();
    }

    // §11: after max attempts → 401 "too many attempts"; the code is consumed (revoked).
    @Test
    void verifyCodeAfterMaxAttemptsConsumesCodeAndThrows() {
        LoginCode code = outstandingCode();
        code.setAttempts(5); // at the cap
        when(loginCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "owner@shop.com", OtpPurpose.ACCOUNT_DELETION))
            .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> otpService.verifyCode("owner@shop.com", "123456",
                OtpPurpose.ACCOUNT_DELETION))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Too many attempts");

        assertThat(code.getConsumedAt()).isNotNull(); // consumed — no more retries on this code
    }

    // §11: valid code → consumed + email returned (the path account-deletion confirm relies on).
    @Test
    void verifyCodeWithValidCodeConsumesAndReturnsEmail() {
        LoginCode code = outstandingCode();
        when(loginCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "owner@shop.com", OtpPurpose.ACCOUNT_DELETION))
            .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        String email = otpService.verifyCode("owner@shop.com", "123456",
            OtpPurpose.ACCOUNT_DELETION);

        assertThat(email).isEqualTo("owner@shop.com");
        assertThat(code.getConsumedAt()).isNotNull();
    }

    // Sanity: the existing verify() path still routes LOGIN/SIGNUP through verifyCode (the
    // extraction didn't break login). A LOGIN verify with a valid code delegates to loginExisting.
    @Test
    void verifyDelegatesToVerifyCodeForLogin() {
        LoginCode code = outstandingCode();
        code.setPurpose(OtpPurpose.LOGIN);
        when(loginCodeRepository.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "owner@shop.com", OtpPurpose.LOGIN))
            .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);
        com.vyapaarmitra.api.user.User user = new com.vyapaarmitra.api.user.User();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@shop.com");
        user.setActive(true);
        user.setEmailVerified(true);
        when(userRepository.findByEmailIgnoreCase("owner@shop.com")).thenReturn(Optional.of(user));
        com.vyapaarmitra.api.membership.Membership m = new com.vyapaarmitra.api.membership.Membership();
        m.setUserId(user.getId());
        when(membershipService.defaultActive(any(), any())).thenReturn(m);
        when(tokenIssuer.issue(any(), any())).thenReturn(
            new TokenResponse("access", "refresh", null, false, null));

        TokenResponse response = otpService.verify(new OtpVerifyRequest(
            "owner@shop.com", "123456", OtpPurpose.LOGIN, null, null, null));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(code.getConsumedAt()).isNotNull();
        verify(tokenIssuer).issue(any(), any());
    }
}