package com.vyapaarmitra.api.accountdeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.auth.JwtService;
import com.vyapaarmitra.api.auth.TokenIssuer;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.subscription.PlanService;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * §11: the login/token response carries {@code pendingDeletion} + {@code scheduledAt} so clients
 * route to the reactivation interstitial. Verified via {@link TokenIssuer#issue}, the single place
 * every auth path (password / OTP / Google / refresh) builds the session.
 */
class TokenIssuerPendingDeletionTest {

    @Test
    void tokenResponseFlagsPendingDeletionWhenScheduledAtSet() {
        JwtService jwtService = mock(JwtService.class);
        BusinessRepository businessRepository = mock(BusinessRepository.class);
        PlanService planService = mock(PlanService.class);
        TokenIssuer issuer = new TokenIssuer(jwtService, businessRepository, planService);

        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plus(20, ChronoUnit.DAYS);
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@shop.com");
        user.setFullName("Owner");
        user.setDeletionScheduledAt(scheduledAt);

        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setBusinessId(businessId);
        membership.setRole(Role.OWNER);

        when(jwtService.createAccessToken(any(), any())).thenReturn("access");
        when(jwtService.createRefreshToken(any(), any())).thenReturn("refresh");
        when(businessRepository.findById(businessId))
            .thenReturn(Optional.of(businessNamed(businessId, "Shop")));
        when(planService.view(businessId)).thenReturn(null);

        TokenResponse response = issuer.issue(user, membership);

        assertThat(response.pendingDeletion()).isTrue();
        assertThat(response.scheduledAt()).isEqualTo(scheduledAt.toString());
    }

    @Test
    void tokenResponseOmitsScheduleWhenAccountIsActive() {
        JwtService jwtService = mock(JwtService.class);
        BusinessRepository businessRepository = mock(BusinessRepository.class);
        PlanService planService = mock(PlanService.class);
        TokenIssuer issuer = new TokenIssuer(jwtService, businessRepository, planService);

        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("owner@shop.com");
        user.setFullName("Owner");
        // deletionScheduledAt stays null

        Membership membership = new Membership();
        membership.setUserId(userId);
        membership.setBusinessId(businessId);
        membership.setRole(Role.OWNER);

        when(jwtService.createAccessToken(any(), any())).thenReturn("access");
        when(jwtService.createRefreshToken(any(), any())).thenReturn("refresh");
        when(businessRepository.findById(businessId))
            .thenReturn(Optional.of(businessNamed(businessId, "Shop")));
        when(planService.view(businessId)).thenReturn(null);

        TokenResponse response = issuer.issue(user, membership);

        assertThat(response.pendingDeletion()).isFalse();
        assertThat(response.scheduledAt()).isNull();
    }

    private Business businessNamed(UUID id, String name) {
        Business b = new Business();
        b.setId(id);
        b.setName(name);
        return b;
    }
}