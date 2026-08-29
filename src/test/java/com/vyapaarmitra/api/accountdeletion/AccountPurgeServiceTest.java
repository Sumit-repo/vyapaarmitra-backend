package com.vyapaarmitra.api.accountdeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.media.MediaService;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountPurgeServiceTest {

    @Mock private AccountDeletionRequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MediaService mediaService;
    @Mock private EntityManager em;
    @Mock private Query query;

    private AccountPurgeService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountPurgeService(requestRepository, userRepository, membershipRepository,
            mediaService);
        Field emField = AccountPurgeService.class.getDeclaredField("em");
        emField.setAccessible(true);
        emField.set(service, em);
        // Native queries are chained: createNativeQuery(...).setParameter(...).executeUpdate()/getResultList()
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getResultList()).thenReturn(List.of());
    }

    private AccountDeletionRequest openRequest(UUID id, UUID userId) {
        AccountDeletionRequest r = new AccountDeletionRequest();
        r.setId(id);
        r.setUserId(userId);
        return r;
    }

    private User userWithEmail(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private Membership membership(UUID userId, UUID businessId, Role role) {
        Membership m = new Membership();
        m.setUserId(userId);
        m.setBusinessId(businessId);
        m.setRole(role);
        return m;
    }

    // §11: user past scheduled_at → user + owned businesses hard-deleted; ALL business-scoped
    // tables cleared; media delete attempted; completed_at set.
    @Test
    void purgeRunsFullCascadeForOwnedBusinessAndStampsCompleted() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        AccountDeletionRequest request = openRequest(requestId, userId);
        User user = userWithEmail(userId, "owner@shop.com");
        user.setAvatarPublicId("avatars/abc");
        Membership owner = membership(userId, businessId, Role.OWNER);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(owner));

        int businesses = service.purgeAccount(requestId);

        assertThat(businesses).isEqualTo(1);
        // Every business-scoped table in the api/* packages, in FK-dependency order:
        verify(em).createNativeQuery("delete from reminder_logs where business_id = :bid");
        verify(em).createNativeQuery("delete from customer_reminder_settings where business_id = :bid");
        verify(em).createNativeQuery("delete from defaulter_reports where business_id = :bid");
        verify(em).createNativeQuery("delete from share_links where business_id = :bid");
        verify(em).createNativeQuery("delete from invoices where business_id = :bid");
        verify(em).createNativeQuery("delete from ledger_entries where business_id = :bid");
        verify(em).createNativeQuery("delete from supplier_ledger_entries where business_id = :bid");
        verify(em).createNativeQuery("delete from suppliers where business_id = :bid");
        verify(em).createNativeQuery("delete from customers where business_id = :bid");
        verify(em).createNativeQuery("delete from message_templates where business_id = :bid");
        verify(em).createNativeQuery("delete from subscriptions where business_id = :bid");
        verify(em).createNativeQuery("delete from memberships where business_id = :bid");
        verify(em).createNativeQuery("delete from user_branch_access where branch_id in "
            + "(select id from branches where business_id = :bid)");
        verify(em).createNativeQuery("delete from branches where business_id = :bid");
        verify(em).createNativeQuery("delete from businesses where id = :bid");
        // Media best-effort: avatar + any attachment public_ids (none here) attempted.
        verify(mediaService).delete("avatars/abc");
        verify(userRepository).delete(user);
        assertThat(request.getCompletedAt()).isNotNull();
    }

    // §11: identity that is STAFF in another business → only that membership removed; the other
    // business survives (no business-scoped delete runs against it).
    @Test
    void purgeRemovesOnlyNonOwnedMembershipLeavingOtherBusinessIntact() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherBusinessId = UUID.randomUUID();
        AccountDeletionRequest request = openRequest(requestId, userId);
        User user = userWithEmail(userId, "staff@shop.com");
        Membership staff = membership(userId, otherBusinessId, Role.STAFF);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(staff));

        int businesses = service.purgeAccount(requestId);

        assertThat(businesses).isZero(); // no owned business purged
        // The user's own memberships (all roles) are cleared via the user-scoped delete...
        verify(em).createNativeQuery("delete from membership_branch_access where membership_id in "
            + "(select id from memberships where user_id = :uid)");
        verify(em).createNativeQuery("delete from memberships where user_id = :uid");
        // ...but NO business-scoped cascade runs for the other business.
        verify(em, never()).createNativeQuery(eq("delete from customers where business_id = :bid"));
        verify(em, never()).createNativeQuery(eq("delete from businesses where id = :bid"));
        verify(userRepository).delete(user);
        assertThat(request.getCompletedAt()).isNotNull();
    }

    // §11: owner of multiple businesses → all owned businesses purged.
    @Test
    void purgeCascadeRunsForEveryOwnedBusiness() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        AccountDeletionRequest request = openRequest(requestId, userId);
        User user = userWithEmail(userId, "owner@shop.com");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndActiveTrue(userId))
            .thenReturn(List.of(membership(userId, b1, Role.OWNER),
                                membership(userId, b2, Role.OWNER)));

        int businesses = service.purgeAccount(requestId);

        assertThat(businesses).isEqualTo(2);
        // The businesses-row delete fires once per owned business.
        verify(em, org.mockito.Mockito.times(2))
            .createNativeQuery(eq("delete from businesses where id = :bid"));
        verify(userRepository).delete(user);
    }

    // §11: purge run twice (idempotency) → second run is a safe no-op.
    @Test
    void purgeIsIdempotentOnSecondRunAfterCompletion() {
        UUID requestId = UUID.randomUUID();
        AccountDeletionRequest request = openRequest(requestId, UUID.randomUUID());
        request.setCompletedAt(java.time.Instant.now()); // already purged
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        int businesses = service.purgeAccount(requestId);

        assertThat(businesses).isZero();
        verify(userRepository, never()).delete(any());
        verify(em, never()).createNativeQuery(eq("delete from businesses where id = :bid"));
    }

    @Test
    void purgeIsIdempotentNoOpWhenCancelled() {
        UUID requestId = UUID.randomUUID();
        AccountDeletionRequest request = openRequest(requestId, UUID.randomUUID());
        request.setCancelledAt(java.time.Instant.now());
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        int businesses = service.purgeAccount(requestId);

        assertThat(businesses).isZero();
        verify(userRepository, never()).delete(any());
        verify(em, never()).createNativeQuery(eq("delete from businesses where id = :bid"));
    }

    // §11: one account errors mid-batch → batch continues; failure logged; others still purged.
    @Test
    void purgeExpiredIsolatesPerAccountFailures() {
        UUID okId = UUID.randomUUID();
        UUID okUserId = UUID.randomUUID();
        UUID okBusiness = UUID.randomUUID();
        AccountDeletionRequest ok = openRequest(okId, okUserId);
        AccountDeletionRequest bad = openRequest(UUID.randomUUID(), UUID.randomUUID());

        when(requestRepository.findByCancelledAtIsNullAndCompletedAtIsNullAndScheduledAtLessThan(
                any(), any())).thenReturn(List.of(bad, ok));
        // The "bad" account blows up when its request is re-read... actually findById returns it;
        // simulate failure at user lookup (user gone mid-batch → NPE path is handled, so throw
        // from userRepository to simulate a real mid-batch error).
        when(requestRepository.findById(bad.getId())).thenReturn(Optional.of(bad));
        when(userRepository.findById(bad.getUserId()))
            .thenThrow(new RuntimeException("transient boom"));
        // The "ok" account purges normally.
        when(requestRepository.findById(ok.getId())).thenReturn(Optional.of(ok));
        User okUser = userWithEmail(okUserId, "owner@shop.com");
        when(userRepository.findById(okUserId)).thenReturn(Optional.of(okUser));
        when(membershipRepository.findByUserIdAndActiveTrue(okUserId))
            .thenReturn(List.of(membership(okUserId, okBusiness, Role.OWNER)));

        AccountPurgeService.PurgeResult result = service.purgeExpired();

        // The failed account didn't abort the batch: the ok account still purged.
        assertThat(result.purgedUsers()).isEqualTo(1);
        assertThat(result.purgedBusinesses()).isEqualTo(1);
        verify(userRepository).delete(okUser);
        // The failed request was NOT marked completed (it will be retried next sweep).
        assertThat(bad.getCompletedAt()).isNull();
    }

    // §11: user not yet past scheduled_at → untouched (the worklist query returns nothing).
    @Test
    void purgeExpiredTouchesNothingWhenNoAccountIsDue() {
        when(requestRepository.findByCancelledAtIsNullAndCompletedAtIsNullAndScheduledAtLessThan(
                any(), any())).thenReturn(List.of());

        AccountPurgeService.PurgeResult result = service.purgeExpired();

        assertThat(result.purgedUsers()).isZero();
        assertThat(result.purgedBusinesses()).isZero();
        verify(userRepository, never()).delete(any());
    }
}