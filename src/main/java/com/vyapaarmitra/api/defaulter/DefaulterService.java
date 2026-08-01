package com.vyapaarmitra.api.defaulter;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.defaulter.DefaulterDtos.DefaulterReportView;
import com.vyapaarmitra.api.reminder.ReminderLog;
import com.vyapaarmitra.api.reminder.ReminderLogRepository;
import com.vyapaarmitra.api.reminder.ReminderOutcome;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-business defaulter network — the write side (Phase A, no cross-merchant visibility).
 * A merchant may warn a customer only once they're 90+ days overdue; the warning starts a
 * 7-day grace (activation is the scheduled job's job), and a settling payment clears it.
 * Full spec + legal boundaries: web docs/defaulter-network.md.
 */
@Service
public class DefaulterService {

    static final int MIN_OVERDUE_DAYS = 90;
    static final int GRACE_DAYS = 7;

    private final CustomerService customerService;
    private final DefaulterReportRepository reportRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final UserRepository userRepository;
    private final AppTime appTime;

    public DefaulterService(CustomerService customerService,
                            DefaulterReportRepository reportRepository,
                            ReminderLogRepository reminderLogRepository,
                            UserRepository userRepository,
                            AppTime appTime) {
        this.customerService = customerService;
        this.reportRepository = reportRepository;
        this.reminderLogRepository = reminderLogRepository;
        this.userRepository = userRepository;
        this.appTime = appTime;
    }

    /**
     * Merchant sends the defaulter warning for one of their 90+-days-overdue customers. Logs
     * the warning (the required one communication) and starts the 7-day grace.
     */
    @Transactional
    public DefaulterReportView warn(AuthUser authUser, UUID customerId) {
        requireConsent(authUser.id());
        Customer customer = customerService.loadAccessible(authUser, customerId);

        long overdueDays = overdueDays(customer);
        if (overdueDays < MIN_OVERDUE_DAYS) {
            throw ApiException.badRequest("NOT_QUALIFIED",
                "You can only warn a customer once they are " + MIN_OVERDUE_DAYS + "+ days overdue.");
        }
        String phone = normalizePhone(customer.getPhone());
        if (phone == null) {
            throw ApiException.badRequest("NO_PHONE",
                "This customer needs a phone number before they can be reported.");
        }

        logWarning(customer, authUser);

        Instant now = Instant.now();
        DefaulterReport report = reportRepository
            .findByNormalizedPhoneAndBusinessId(phone, customer.getBusinessId())
            .orElseGet(DefaulterReport::new);
        report.setNormalizedPhone(phone);
        report.setBusinessId(customer.getBusinessId());
        report.setReportedByUserId(authUser.id());
        report.setCustomerId(customer.getId());
        report.setStatus(DefaulterStatus.WARNING);
        report.setOverdueDaysAtReport((int) overdueDays);
        report.setWarningSentAt(now);
        report.setActivatedAt(null);
        report.setClearedAt(null);
        reportRepository.save(report);

        return new DefaulterReportView(report.getStatus(), now, now.plus(GRACE_DAYS, ChronoUnit.DAYS));
    }

    /**
     * Network signal for an exact phone: is it actively flagged by anyone? Anonymized —
     * callers learn only true/false, never who/how much. Exact match only (no search).
     * (Consent reciprocity gating lands in Phase C.)
     */
    @Transactional(readOnly = true)
    public boolean isPhoneFlagged(UUID callerUserId, String rawPhone) {
        // Reciprocity: you only see the network if you've consented to be part of it.
        if (!isConsented(callerUserId)) {
            return false;
        }
        String phone = normalizePhone(rawPhone);
        if (phone == null) {
            return false;
        }
        return reportRepository.existsByNormalizedPhoneAndStatus(phone, DefaulterStatus.ACTIVE);
    }

    /** Whether the identity has opted into the defaulter network (for the Settings toggle). */
    @Transactional(readOnly = true)
    public boolean isConsented(UUID userId) {
        return userRepository.findById(userId).map(User::isDefaulterNetworkConsent).orElse(false);
    }

    /** Opt in/out of the defaulter network (identity-level, applies to all the user's shops). */
    @Transactional
    public boolean setConsent(UUID userId, boolean consent) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Account not found"));
        user.setDefaulterNetworkConsent(consent);
        userRepository.save(user);
        return consent;
    }

    private void requireConsent(UUID userId) {
        if (!isConsented(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED",
                "Turn on the defaulter network in Settings to report or see flagged customers.");
        }
    }

    /**
     * Auto-clears any live report for a customer once their balance is settled (pay-to-clear).
     * Called from the ledger after a payment recomputes the balance.
     */
    @Transactional
    public void clearForCustomerIfSettled(Customer customer) {
        if (customer.getCurrentBalance().signum() > 0) {
            return;
        }
        List<DefaulterReport> live = reportRepository.findByCustomerIdAndStatusIn(
            customer.getId(), List.of(DefaulterStatus.WARNING, DefaulterStatus.ACTIVE));
        if (live.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (DefaulterReport r : live) {
            r.setStatus(DefaulterStatus.CLEARED);
            r.setClearedAt(now);
        }
        reportRepository.saveAll(live);
    }

    private void logWarning(Customer customer, AuthUser authUser) {
        ReminderLog log = new ReminderLog();
        log.setBusinessId(customer.getBusinessId());
        log.setBranchId(customer.getBranchId());
        log.setCustomerId(customer.getId());
        log.setChannel("SMS");
        log.setOutcome(ReminderOutcome.REMINDER_SENT);
        log.setNote("Defaulter warning sent (90+ days overdue).");
        log.setCreatedBy(authUser.id());
        reminderLogRepository.save(log);
    }

    private long overdueDays(Customer customer) {
        LocalDate due = customer.getOldestDueDate();
        if (customer.getCurrentBalance().signum() <= 0 || due == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(due, appTime.today());
    }

    /**
     * Canonical 10-digit form for the network key: digits only, with a leading country code
     * (+91) or trunk 0 stripped. Best-effort — anything that doesn't reduce to 10 digits is
     * used as-is so we never silently merge distinct numbers.
     */
    static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.isEmpty() ? null : digits;
    }
}
