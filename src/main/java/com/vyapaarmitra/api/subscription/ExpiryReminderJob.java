package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.email.EmailSender;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Emails a shop owner ~3 days before their paid (one-time, non-renewing) plan lapses, so they can
 * extend before losing access — the re-purchase driver for the no-auto-renew model. Dedup is
 * structural: the daily scan uses a 1-day-wide window ([now+2d, now+3d]), so each subscription
 * lands in it on exactly one run — no "reminded" flag/column needed. No-ops cleanly when email
 * delivery isn't configured (Resend key blank), so dev/test runs without sending anything.
 */
@Component
@Slf4j
public class ExpiryReminderJob {

    private final SubscriptionRepository subscriptionRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    public ExpiryReminderJob(SubscriptionRepository subscriptionRepository,
                             MembershipRepository membershipRepository,
                             UserRepository userRepository, EmailSender emailSender) {
        this.subscriptionRepository = subscriptionRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
    }

    /** Daily at 09:00 IST — a reasonable hour to land a "your plan expires soon" nudge. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void remindExpiringSoon() {
        if (!emailSender.isLiveDelivery()) {
            return; // no email provider configured — nothing to send
        }
        Instant now = Instant.now();
        List<Subscription> expiring = subscriptionRepository.findByStatusAndCurrentPeriodEndBetween(
            SubscriptionStatus.ACTIVE, now.plus(2, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS));
        int sent = 0;
        for (Subscription sub : expiring) {
            if (remind(sub)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Sent {} plan-expiry reminder email(s)", sent);
        }
    }

    private boolean remind(Subscription sub) {
        User owner = membershipRepository.findByBusinessIdOrderByCreatedAtAsc(sub.getBusinessId()).stream()
            .filter(m -> m.getRole() == Role.OWNER)
            .findFirst()
            .flatMap(m -> userRepository.findById(m.getUserId()))
            .orElse(null);
        if (owner == null || owner.getEmail() == null || owner.getEmail().isBlank()) {
            return false;
        }
        String plan = sub.getPlan() == null ? "plan" : sub.getPlan().name();
        String subject = "Your VyapaarMitra " + plan + " plan expires in 3 days";
        String html = """
            <p>Namaste %s,</p>
            <p>Your VyapaarMitra <b>%s</b> plan expires in about 3 days. It won't renew automatically —
            open the app and tap <b>Extend</b> to keep your features (recovery, reports, staff and more)
            without a break.</p>
            <p>Thank you for using VyapaarMitra.</p>
            """.formatted(safeName(owner.getFullName()), plan);
        try {
            emailSender.send(owner.getEmail(), subject, html);
            return true;
        } catch (RuntimeException e) {
            log.warn("Expiry reminder email failed for business {}", sub.getBusinessId(), e);
            return false;
        }
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "there" : name;
    }
}
