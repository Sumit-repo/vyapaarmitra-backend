package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.email.EmailSender;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled housekeeping for account deletion: the daily purge sweep and the T-3-day "your account
 * deletes soon" reminder.
 *
 * <p>⚠️ {@code @Scheduled} on Cloud Run only fires while an instance is alive. Set
 * <strong>{@code min-instances=1}</strong> (or drive {@code POST /internal/accounts/purge} from
 * Cloud Scheduler) or these jobs are unreliable. See docs/account-deletion.md §1.
 */
@Slf4j
@Component
public class AccountDeletionMaintenance {

    private static final int REMINDER_BATCH = 500;
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Asia/Kolkata"));

    private final AccountPurgeService purgeService;
    private final AccountDeletionRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    public AccountDeletionMaintenance(AccountPurgeService purgeService,
                                      AccountDeletionRequestRepository requestRepository,
                                      UserRepository userRepository, EmailSender emailSender) {
        this.purgeService = purgeService;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
    }

    /** Daily at 00:30 IST — grace is measured in days, so the exact minute doesn't matter. */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Kolkata")
    public void purgeSweep() {
        purgeService.purgeExpired();
    }

    /**
     * Daily at 00:35 IST — email owners whose account deletes in ~3 days a "tap to keep it" nudge.
     * A 24h [now+3d, now+4d) window over a once-daily run fires the reminder exactly once.
     */
    @Scheduled(cron = "0 35 0 * * *", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void sendThreeDayReminders() {
        Instant from = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant to = from.plus(1, ChronoUnit.DAYS);
        List<AccountDeletionRequest> due = requestRepository
            .findByCancelledAtIsNullAndCompletedAtIsNullAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                from, to, Limit.of(REMINDER_BATCH));
        int sent = 0;
        for (AccountDeletionRequest request : due) {
            User user = userRepository.findById(request.getUserId()).orElse(null);
            if (user == null || user.getDeletionScheduledAt() == null) {
                continue;
            }
            try {
                emailSender.send(user.getEmail(),
                    "Your VyapaarMitra account deletes in 3 days",
                    reminderBody(request.getScheduledAt()));
                sent++;
            } catch (RuntimeException e) {
                log.warn("T-3 deletion reminder failed for user {}: {}",
                    request.getUserId(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Sent {} T-3-day account-deletion reminder(s)", sent);
        }
    }

    private String reminderBody(Instant scheduledAt) {
        String date = DATE_FMT.format(scheduledAt);
        return "<div style=\"font-family:sans-serif;max-width:460px\">"
            + "<h2 style=\"margin:0 0 8px\">Your account deletes in 3 days</h2>"
            + "<p style=\"color:#555\">Your VyapaarMitra account and khata are scheduled to be"
            + " permanently deleted on <strong>" + date + "</strong>. This cannot be undone.</p>"
            + "<p style=\"color:#555\">Want to keep everything? Open the app and tap"
            + " <strong>Reactivate my account</strong> — your khata stays exactly as it is.</p></div>";
    }
}
