package com.vyapaarmitra.api.defaulter;

import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flips WARNING reports whose 7-day grace has lapsed: still-unpaid → ACTIVE (badge goes live);
 * already-settled → CLEARED. Pay-to-clear usually handles settled ones first, but this is the
 * safety net for partial-then-full payments and rounding.
 */
@Component
@Slf4j
public class DefaulterMaintenance {

    private final DefaulterReportRepository reportRepository;
    private final CustomerRepository customerRepository;

    public DefaulterMaintenance(DefaulterReportRepository reportRepository,
                                CustomerRepository customerRepository) {
        this.reportRepository = reportRepository;
        this.customerRepository = customerRepository;
    }

    /** Daily at 00:20 IST — grace is measured in days, so exact time doesn't matter. */
    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void activateLapsedWarnings() {
        Instant cutoff = Instant.now().minus(DefaulterService.GRACE_DAYS, ChronoUnit.DAYS);
        List<DefaulterReport> due = reportRepository
            .findByStatusAndWarningSentAtLessThanEqual(DefaulterStatus.WARNING, cutoff);
        if (due.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int activated = 0;
        for (DefaulterReport r : due) {
            Customer c = customerRepository.findById(r.getCustomerId()).orElse(null);
            boolean stillOwed = c != null && c.getCurrentBalance().signum() > 0;
            if (stillOwed) {
                r.setStatus(DefaulterStatus.ACTIVE);
                r.setActivatedAt(now);
                activated++;
            } else {
                r.setStatus(DefaulterStatus.CLEARED);
                r.setClearedAt(now);
            }
        }
        reportRepository.saveAll(due);
        log.info("Defaulter grace check: {} activated, {} cleared", activated, due.size() - activated);
    }
}
