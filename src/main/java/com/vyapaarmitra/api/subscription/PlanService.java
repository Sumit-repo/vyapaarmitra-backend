package com.vyapaarmitra.api.subscription;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.invoice.BillType;
import com.vyapaarmitra.api.invoice.InvoiceRepository;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.subscription.PlanDtos.PlanView;
import com.vyapaarmitra.api.subscription.PlanDtos.Usage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of billing: turns a stored {@link Subscription} into the effective
 * plan, live usage, and the client-facing {@link PlanView}. This is the single
 * place that applies the trial and dunning-grace windows — nothing stores the
 * "effective" plan, it's always derived against {@code now}.
 */
@Service
public class PlanService {

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AppTime appTime;
    /** Self-reference so the lazy-create runs through the proxy (REQUIRES_NEW). */
    private final PlanService self;

    public PlanService(SubscriptionRepository subscriptionRepository,
                       InvoiceRepository invoiceRepository,
                       LedgerEntryRepository ledgerEntryRepository,
                       AppTime appTime,
                       @Lazy @Autowired PlanService self) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.appTime = appTime;
        this.self = self;
    }

    /** The row for a business, lazily seeding a fresh 14-day trial if somehow absent. */
    @Transactional(readOnly = true)
    public Subscription getOrCreate(UUID businessId) {
        return subscriptionRepository.findByBusinessId(businessId)
            // The read paths (AuthService.me/login/refresh) run read-only; do the
            // insert in its own read-write tx so it actually flushes/commits. Backfill
            // + trial bootstrap mean this branch shouldn't fire in practice — it's a safety net.
            .orElseGet(() -> self.createTrial(businessId));
    }

    /** Insert (or return an already-created) trial row in an isolated read-write tx. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subscription createTrial(UUID businessId) {
        // Re-check inside the new tx so two concurrent callers don't double-insert
        // (business_id is unique, but this avoids the constraint-violation path).
        return subscriptionRepository.findByBusinessId(businessId)
            .orElseGet(() -> {
                Subscription sub = new Subscription();
                sub.setBusinessId(businessId);
                sub.setPlan(PlanTier.FREE);
                sub.setStatus(SubscriptionStatus.TRIALING);
                sub.setTrialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS));
                return subscriptionRepository.save(sub);
            });
    }

    /** The plan whose entitlements apply right now (trial → PRO, then grace, else FREE). */
    public PlanTier effectivePlan(Subscription sub, Instant now) {
        Instant paidUntil = latest(sub.getCurrentPeriodEnd(), sub.getGraceUntil());
        // ACTIVE, or CANCELLED-at-period-end: keep the paid plan until the period runs out.
        boolean paidAccess = sub.getStatus() == SubscriptionStatus.ACTIVE
            || sub.getStatus() == SubscriptionStatus.CANCELLED;
        if (paidAccess && paidUntil != null && !now.isAfter(paidUntil)) {
            return sub.getPlan();
        }
        if (sub.getStatus() == SubscriptionStatus.PAST_DUE
            && sub.getGraceUntil() != null && !now.isAfter(sub.getGraceUntil())) {
            return sub.getPlan();
        }
        if (sub.getTrialEndsAt() != null && !now.isAfter(sub.getTrialEndsAt())) {
            return PlanTier.PRO;
        }
        return PlanTier.FREE;
    }

    @Transactional
    public PlanTier effectivePlan(UUID businessId) {
        return effectivePlan(getOrCreate(businessId), Instant.now());
    }

    @Transactional
    public PlanCatalog.Entitlements entitlements(UUID businessId) {
        return PlanCatalog.entitlements(effectivePlan(businessId));
    }

    @Transactional
    public PlanView view(AuthUser authUser) {
        return view(authUser.businessId());
    }

    @Transactional
    public PlanView view(UUID businessId) {
        Subscription sub = getOrCreate(businessId);
        Instant now = Instant.now();
        PlanTier effective = effectivePlan(sub, now);
        boolean trialActive = sub.getStatus() == SubscriptionStatus.TRIALING
            && sub.getTrialEndsAt() != null && now.isBefore(sub.getTrialEndsAt());
        int daysLeft = trialActive
            ? (int) Math.ceil((sub.getTrialEndsAt().toEpochMilli() - now.toEpochMilli()) / (double) DAY_MS)
            : 0;
        return new PlanView(effective, sub.getPlan(), trialActive, daysLeft, usage(businessId));
    }

    /** Live usage against the caps, computed in the business timezone (Asia/Kolkata). */
    @Transactional(readOnly = true)
    public Usage usage(UUID businessId) {
        LocalDate today = appTime.today();
        Instant dayStart = appTime.startOfDay(today);
        Instant dayEnd = appTime.startOfDay(today.plusDays(1));
        Instant monthStart = appTime.startOfDay(today.withDayOfMonth(1));
        Instant monthEnd = appTime.startOfDay(today.withDayOfMonth(1).plusMonths(1));

        long entriesToday = invoiceRepository.countCreatedBetween(businessId, dayStart, dayEnd)
            + ledgerEntryRepository.countByBusinessBetween(businessId, dayStart, dayEnd);
        long pakkaThisMonth = invoiceRepository.countByTypeCreatedBetween(
            businessId, BillType.PAKKA, monthStart, monthEnd);
        return new Usage(entriesToday, pakkaThisMonth);
    }

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
