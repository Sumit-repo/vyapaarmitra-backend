package com.vyapaarmitra.api.accountdeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private OtpService otpService;
    @Mock private UserRepository userRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private AccountDeletionRequestRepository requestRepository;
    @Mock private EmailSender emailSender;

    private AccountDeletionService service() {
        return new AccountDeletionService(otpService, userRepository, businessRepository,
            membershipRepository, requestRepository, emailSender);
    }

    private User user(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("owner@shop.com");
        return u;
    }

    private Membership ownerMembership(UUID userId, UUID businessId) {
        Membership m = new Membership();
        m.setUserId(userId);
        m.setBusinessId(businessId);
        m.setRole(Role.OWNER);
        return m;
    }

    @Test
    void confirmVerifiesOtpAndSchedulesUserAndOwnedBusinesses() {
        UUID userId = UUID.randomUUID();
        UUID businessId1 = UUID.randomUUID();
        UUID businessId2 = UUID.randomUUID();
        User user = user(userId);
        Business b1 = new Business();
        b1.setId(businessId1);
        Business b2 = new Business();
        b2.setId(businessId2);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndActiveTrue(userId))
            .thenReturn(List.of(ownerMembership(userId, businessId1),
                                ownerMembership(userId, businessId2)));
        when(businessRepository.findAllById(any())).thenReturn(List.of(b1, b2));
        when(requestRepository.findFirstByUserIdAndCancelledAtIsNullAndCompletedAtIsNull(userId))
            .thenReturn(Optional.empty());

        var response = service().confirm(userId, DeletionReason.TOO_EXPENSIVE, "  too costly ", "123456");

        verify(otpService).verifyCode(eq("owner@shop.com"), eq("123456"),
            eq(OtpPurpose.ACCOUNT_DELETION));
        // §11: deletion_scheduled_at set on the user AND EVERY owned business.
        assertThat(user.getDeletionScheduledAt()).isNotNull();
        assertThat(b1.getDeletionScheduledAt()).isEqualTo(user.getDeletionScheduledAt());
        assertThat(b2.getDeletionScheduledAt()).isEqualTo(user.getDeletionScheduledAt());
        assertThat(response.scheduledAt()).isNotBlank();

        // §11: an account_deletion_requests row is created with scheduled_at = requested_at + 30d.
        ArgumentCaptor<AccountDeletionRequest> captor =
            ArgumentCaptor.forClass(AccountDeletionRequest.class);
        verify(requestRepository).save(captor.capture());
        AccountDeletionRequest saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getReason()).isEqualTo(DeletionReason.TOO_EXPENSIVE);
        assertThat(saved.getFeedback()).isEqualTo("too costly"); // trimmed; blank→null happens for blanks
        assertThat(saved.getScheduledAt()).isNotNull();
        // scheduled_at is requested_at + 30d; in a unit test @CreationTimestamp hasn't fired, so
        // assert it lands ~30 days from now rather than against the null requested_at.
        assertThat(saved.getScheduledAt()).isBetween(
            Instant.now().plus(AccountDeletionService.GRACE_DAYS - 1, ChronoUnit.DAYS),
            Instant.now().plus(AccountDeletionService.GRACE_DAYS + 1, ChronoUnit.DAYS));
        verify(emailSender).send(eq("owner@shop.com"), any(), any());
    }

    @Test
    void confirmWithNoReasonOrFeedbackStoresNulls() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        User user = user(userId);
        Business business = new Business();
        business.setId(businessId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndActiveTrue(userId))
            .thenReturn(List.of(ownerMembership(userId, businessId)));
        when(businessRepository.findAllById(List.of(businessId))).thenReturn(List.of(business));
        when(requestRepository.findFirstByUserIdAndCancelledAtIsNullAndCompletedAtIsNull(userId))
            .thenReturn(Optional.empty());

        service().confirm(userId, null, "   ", "123456");

        ArgumentCaptor<AccountDeletionRequest> captor =
            ArgumentCaptor.forClass(AccountDeletionRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isNull();
        assertThat(captor.getValue().getFeedback()).isNull(); // blank → null
    }

    @Test
    void confirmWithWrongOtpMakesNoStateChange() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(otpService.verifyCode(any(), any(), any()))
            .thenThrow(ApiException.unauthorized("Invalid or expired code"));

        assertThatThrownBy(() -> service().confirm(userId, null, null, "000000"))
            .isInstanceOf(ApiException.class);

        // §11: 401 with NO state change — nothing persisted, no email, no business mutation.
        verify(userRepository, never()).save(any());
        verify(businessRepository, never()).saveAll(any());
        verify(requestRepository, never()).save(any());
        verify(emailSender, never()).send(any(), any(), any());
        assertThat(user.getDeletionScheduledAt()).isNull();
    }

    @Test
    void confirmWhenAlreadyPendingReusesOpenRequestNoDuplicate() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        User user = user(userId);
        Business business = new Business();
        business.setId(businessId);
        AccountDeletionRequest existing = new AccountDeletionRequest();
        existing.setUserId(userId);
        existing.setScheduledAt(Instant.now().plus(10, ChronoUnit.DAYS));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndActiveTrue(userId))
            .thenReturn(List.of(ownerMembership(userId, businessId)));
        when(businessRepository.findAllById(List.of(businessId))).thenReturn(List.of(business));
        when(requestRepository.findFirstByUserIdAndCancelledAtIsNullAndCompletedAtIsNull(userId))
            .thenReturn(Optional.of(existing));

        service().confirm(userId, DeletionReason.NOT_USING, null, "123456");

        // §11: idempotent — the SAME open row is updated in place; no new row is inserted.
        ArgumentCaptor<AccountDeletionRequest> captor =
            ArgumentCaptor.forClass(AccountDeletionRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getReason()).isEqualTo(DeletionReason.NOT_USING);
        // Rescheduled to a fresh 30-day window (@CreationTimestamp hasn't fired in a unit test).
        assertThat(captor.getValue().getScheduledAt()).isNotNull();
        assertThat(captor.getValue().getScheduledAt()).isBetween(
            Instant.now().plus(AccountDeletionService.GRACE_DAYS - 1, ChronoUnit.DAYS),
            Instant.now().plus(AccountDeletionService.GRACE_DAYS + 1, ChronoUnit.DAYS));
    }

    @Test
    void cancelClearsMarkersAndStampsRequest() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        User user = user(userId);
        user.setDeletionScheduledAt(java.time.Instant.now());
        Business business = new Business();
        business.setId(businessId);
        business.setDeletionScheduledAt(java.time.Instant.now());
        AccountDeletionRequest open = new AccountDeletionRequest();
        open.setUserId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndActiveTrue(userId))
            .thenReturn(List.of(ownerMembership(userId, businessId)));
        when(businessRepository.findAllById(List.of(businessId))).thenReturn(List.of(business));
        when(requestRepository.findFirstByUserIdAndCancelledAtIsNullAndCompletedAtIsNull(userId))
            .thenReturn(Optional.of(open));

        service().cancel(userId);

        assertThat(user.getDeletionScheduledAt()).isNull();
        assertThat(business.getDeletionScheduledAt()).isNull();
        assertThat(open.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelIsNoOpWhenNotPending() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));

        service().cancel(userId);

        verify(businessRepository, never()).saveAll(any());
        verify(requestRepository, never()).save(any());
    }
}
