package com.vyapaarmitra.api.defaulter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.defaulter.DefaulterDtos.DefaulterReportView;
import com.vyapaarmitra.api.reminder.ReminderLog;
import com.vyapaarmitra.api.reminder.ReminderLogRepository;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaulterServiceTest {

    @Mock private CustomerService customerService;
    @Mock private DefaulterReportRepository reportRepository;
    @Mock private ReminderLogRepository reminderLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppTime appTime;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);
    private final UUID businessId = UUID.randomUUID();
    private final AuthUser owner = new AuthUser(UUID.randomUUID(), businessId, Role.OWNER);

    private DefaulterService service() {
        return new DefaulterService(customerService, reportRepository, reminderLogRepository,
            userRepository, appTime);
    }

    private void callerConsent(boolean consent) {
        User u = new User();
        u.setDefaulterNetworkConsent(consent);
        when(userRepository.findById(owner.id())).thenReturn(Optional.of(u));
    }

    private Customer customer(int overdueDays, BigDecimal balance, String phone) {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setBusinessId(businessId);
        c.setBranchId(UUID.randomUUID());
        c.setPhone(phone);
        c.setCurrentBalance(balance);
        c.setOldestDueDate(balance.signum() > 0 ? TODAY.minusDays(overdueDays) : null);
        return c;
    }

    @Test
    void warnRejectsAnUnconsentedReporter() {
        callerConsent(false);

        assertThatThrownBy(() -> service().warn(owner, UUID.randomUUID()))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("CONSENT_REQUIRED"));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void warnRejectsWhenNotYet90DaysOverdue() {
        callerConsent(true);
        Customer c = customer(30, new BigDecimal("500"), "9876543210");
        when(customerService.loadAccessible(owner, c.getId())).thenReturn(c);
        when(appTime.today()).thenReturn(TODAY);

        assertThatThrownBy(() -> service().warn(owner, c.getId()))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NOT_QUALIFIED"));
        verify(reportRepository, never()).save(any());
        verify(reminderLogRepository, never()).save(any());
    }

    @Test
    void warnRejectsWhenCustomerHasNoPhone() {
        callerConsent(true);
        Customer c = customer(120, new BigDecimal("500"), null);
        when(customerService.loadAccessible(owner, c.getId())).thenReturn(c);
        when(appTime.today()).thenReturn(TODAY);

        assertThatThrownBy(() -> service().warn(owner, c.getId()))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_PHONE"));
    }

    @Test
    void warnLogsAndOpensAWarningReportWhenQualified() {
        callerConsent(true);
        Customer c = customer(120, new BigDecimal("500"), "+91 98765-43210");
        when(customerService.loadAccessible(owner, c.getId())).thenReturn(c);
        when(appTime.today()).thenReturn(TODAY);
        when(reportRepository.findByNormalizedPhoneAndBusinessId("9876543210", businessId))
            .thenReturn(Optional.empty());

        DefaulterReportView view = service().warn(owner, c.getId());

        assertThat(view.status()).isEqualTo(DefaulterStatus.WARNING);
        assertThat(view.activatesAt()).isEqualTo(view.warningSentAt().plusSeconds(7L * 24 * 3600));
        verify(reminderLogRepository).save(any(ReminderLog.class));

        ArgumentCaptor<DefaulterReport> cap = ArgumentCaptor.forClass(DefaulterReport.class);
        verify(reportRepository).save(cap.capture());
        DefaulterReport r = cap.getValue();
        assertThat(r.getStatus()).isEqualTo(DefaulterStatus.WARNING);
        assertThat(r.getNormalizedPhone()).isEqualTo("9876543210");
        assertThat(r.getOverdueDaysAtReport()).isEqualTo(120);
        assertThat(r.getActivatedAt()).isNull();
    }

    @Test
    void isPhoneFlaggedNormalizesThenQueriesActive() {
        callerConsent(true);
        when(reportRepository.existsByNormalizedPhoneAndStatus("9876543210", DefaulterStatus.ACTIVE))
            .thenReturn(true);

        assertThat(service().isPhoneFlagged(owner.id(), "+91 98765-43210")).isTrue();
    }

    @Test
    void isPhoneFlaggedIsFalseForAnUnconsentedCallerAndNeverHitsReports() {
        callerConsent(false);

        assertThat(service().isPhoneFlagged(owner.id(), "9876543210")).isFalse();
        verify(reportRepository, never()).existsByNormalizedPhoneAndStatus(any(), any());
    }

    @Test
    void isPhoneFlaggedIsFalseForBlank() {
        callerConsent(true);

        assertThat(service().isPhoneFlagged(owner.id(), "   ")).isFalse();
        verify(reportRepository, never()).existsByNormalizedPhoneAndStatus(any(), any());
    }

    @Test
    void payToClearClearsLiveReportsWhenSettled() {
        Customer c = customer(0, BigDecimal.ZERO, "9876543210");
        DefaulterReport active = new DefaulterReport();
        active.setStatus(DefaulterStatus.ACTIVE);
        when(reportRepository.findByCustomerIdAndStatusIn(eq(c.getId()), any()))
            .thenReturn(List.of(active));

        service().clearForCustomerIfSettled(c);

        assertThat(active.getStatus()).isEqualTo(DefaulterStatus.CLEARED);
        assertThat(active.getClearedAt()).isNotNull();
        verify(reportRepository).saveAll(List.of(active));
    }

    @Test
    void payToClearIsNoOpWhenBalanceStillOwed() {
        Customer c = customer(120, new BigDecimal("500"), "9876543210");

        service().clearForCustomerIfSettled(c);

        verify(reportRepository, never()).findByCustomerIdAndStatusIn(any(), any());
        verify(reportRepository, never()).saveAll(any());
    }
}
