package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.media.MediaService;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard-deletes accounts whose 30-day grace has lapsed. Idempotent: re-running is safe because
 * every step keys off {@code deletion_scheduled_at < now} and open request rows, and finishes by
 * stamping {@code completed_at}. Each account is purged in its own transaction so one failure
 * (e.g. an FK we missed, a media hiccup) doesn't abort the whole batch. See docs/account-deletion.md
 * §6 for the cascade contract — every business-scoped table in the api/* feature packages is
 * enumerated here; add new ones as features land.
 */
@Slf4j
@Service
public class AccountPurgeService {

    /** Safety cap per sweep so a backlog can't run unbounded; the daily job drains over days. */
    private static final int MAX_PER_SWEEP = 500;

    @PersistenceContext
    private EntityManager em;

    private final AccountDeletionRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final MediaService mediaService;

    public AccountPurgeService(AccountDeletionRequestRepository requestRepository,
                               UserRepository userRepository,
                               MembershipRepository membershipRepository,
                               MediaService mediaService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.mediaService = mediaService;
    }

    /** How many users / businesses one sweep removed — surfaced by the internal endpoint. */
    public record PurgeResult(int purgedUsers, int purgedBusinesses) {
    }

    /**
     * Purge every account whose grace has lapsed. Not transactional itself — each account gets its
     * own {@link #purgeAccount} transaction so failures are isolated.
     */
    public PurgeResult purgeExpired() {
        Instant now = Instant.now();
        List<AccountDeletionRequest> due = requestRepository
            .findByCancelledAtIsNullAndCompletedAtIsNullAndScheduledAtLessThan(now, Limit.of(MAX_PER_SWEEP));
        int users = 0;
        int businesses = 0;
        for (AccountDeletionRequest request : due) {
            try {
                businesses += purgeAccount(request.getId());
                users++;
            } catch (RuntimeException e) {
                // Isolate the failure: log and move on so one bad account can't stall the batch.
                log.error("Account purge failed for request {} (user {})",
                    request.getId(), request.getUserId(), e);
            }
        }
        if (users > 0) {
            log.info("Account purge sweep: {} user(s), {} owned business(es) hard-deleted",
                users, businesses);
        }
        return new PurgeResult(users, businesses);
    }

    /**
     * Purge one account in its own transaction. Re-reads the request row so a concurrent
     * reactivation (cancelled_at set) is honoured. Returns the number of owned businesses removed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeAccount(UUID requestId) {
        AccountDeletionRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getCancelledAt() != null || request.getCompletedAt() != null) {
            return 0; // Reactivated or already purged between selection and now — idempotent no-op.
        }
        UUID userId = request.getUserId();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // User row already gone; just close out the request so we don't reselect it.
            request.setCompletedAt(Instant.now());
            requestRepository.save(request);
            return 0;
        }

        List<Membership> memberships = membershipRepository.findByUserIdAndActiveTrue(userId);
        List<UUID> ownedBusinessIds = memberships.stream()
            .filter(m -> m.getRole() == Role.OWNER)
            .map(Membership::getBusinessId)
            .toList();

        // Best-effort media cleanup BEFORE the rows vanish (we need the public ids).
        deleteBusinessMedia(ownedBusinessIds);
        mediaService.delete(user.getAvatarPublicId());

        int businesses = 0;
        for (UUID businessId : ownedBusinessIds) {
            purgeBusiness(businessId);
            businesses++;
        }

        // Memberships in businesses the user does NOT own: remove the membership only (their
        // business survives). Owned-business memberships were already dropped by purgeBusiness.
        em.createNativeQuery("delete from membership_branch_access where membership_id in "
                + "(select id from memberships where user_id = :uid)")
            .setParameter("uid", userId).executeUpdate();
        em.createNativeQuery("delete from memberships where user_id = :uid")
            .setParameter("uid", userId).executeUpdate();

        // Any outstanding OTP codes for this email.
        em.createNativeQuery("delete from login_codes where lower(email) = lower(:email)")
            .setParameter("email", user.getEmail()).executeUpdate();

        userRepository.delete(user);

        request.setCompletedAt(Instant.now());
        requestRepository.save(request);
        log.info("Purged user {} and {} owned business(es)", userId, businesses);
        return businesses;
    }

    /**
     * Delete every business-scoped row for one business, in FK-dependency order, then the business
     * itself. Native deletes keep this in one auditable place rather than spread across each
     * feature repository. Order matters: children before parents.
     */
    private void purgeBusiness(UUID businessId) {
        // Reminder + reminder-config rows reference customers + templates.
        exec("delete from reminder_logs where business_id = :bid", businessId);
        exec("delete from customer_reminder_settings where business_id = :bid", businessId);
        // Defaulter reports reference customers.
        exec("delete from defaulter_reports where business_id = :bid", businessId);
        // Share links reference customers + invoices.
        exec("delete from share_links where business_id = :bid", businessId);
        // Invoices reference customers + a ledger entry; drop them before ledger + customers.
        exec("delete from invoices where business_id = :bid", businessId);
        // Ledger + supplier ledger reference customers/suppliers.
        exec("delete from ledger_entries where business_id = :bid", businessId);
        exec("delete from supplier_ledger_entries where business_id = :bid", businessId);
        exec("delete from suppliers where business_id = :bid", businessId);
        exec("delete from customers where business_id = :bid", businessId);
        exec("delete from message_templates where business_id = :bid", businessId);
        exec("delete from subscriptions where business_id = :bid", businessId);
        // Memberships for THIS business (all roles) + their branch access.
        exec("delete from membership_branch_access where membership_id in "
            + "(select id from memberships where business_id = :bid)", businessId);
        exec("delete from memberships where business_id = :bid", businessId);
        // user_branch_access is a legacy pre-membership table keyed by branch; clear before branches.
        exec("delete from user_branch_access where branch_id in "
            + "(select id from branches where business_id = :bid)", businessId);
        exec("delete from branches where business_id = :bid", businessId);
        exec("delete from businesses where id = :bid", businessId);
    }

    /** Collect + best-effort Cloudinary-destroy every stored image for the owned businesses. */
    private void deleteBusinessMedia(List<UUID> businessIds) {
        if (businessIds.isEmpty()) {
            return;
        }
        List<String> publicIds = new ArrayList<>();
        publicIds.addAll(mediaPublicIds("ledger_entries", businessIds));
        publicIds.addAll(mediaPublicIds("supplier_ledger_entries", businessIds));
        for (String publicId : publicIds) {
            mediaService.delete(publicId); // best-effort; logs on failure, never throws
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> mediaPublicIds(String table, List<UUID> businessIds) {
        return em.createNativeQuery("select attachment_public_id from " + table
                + " where business_id in (:bids) and attachment_public_id is not null")
            .setParameter("bids", businessIds)
            .getResultList();
    }

    private void exec(String sql, UUID businessId) {
        em.createNativeQuery(sql).setParameter("bid", businessId).executeUpdate();
    }
}
