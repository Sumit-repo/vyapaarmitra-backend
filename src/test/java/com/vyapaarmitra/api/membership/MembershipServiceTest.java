package com.vyapaarmitra.api.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    private MembershipService service() {
        return new MembershipService(membershipRepository);
    }

    private Membership membership(UUID userId, UUID businessId, boolean active, Instant createdAt) {
        Membership m = new Membership();
        m.setId(UUID.randomUUID());
        m.setUserId(userId);
        m.setBusinessId(businessId);
        m.setRole(Role.OWNER);
        m.setActive(active);
        m.setCreatedAt(createdAt);
        return m;
    }

    @Test
    void defaultActivePicksMostRecentlyCreated() {
        UUID userId = UUID.randomUUID();
        Membership older = membership(userId, UUID.randomUUID(), true, Instant.parse("2026-01-01T00:00:00Z"));
        Membership newer = membership(userId, UUID.randomUUID(), true, Instant.parse("2026-06-01T00:00:00Z"));
        when(membershipRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(older, newer));

        assertThat(service().defaultActive(userId)).isSameAs(newer);
    }

    @Test
    void defaultActiveThrowsWhenNoActiveMembership() {
        UUID userId = UUID.randomUUID();
        when(membershipRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service().defaultActive(userId))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_BUSINESS_ACCESS"));
    }

    @Test
    void requireReturnsActiveMembership() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Membership active = membership(userId, businessId, true, Instant.now());
        when(membershipRepository.findByUserIdAndBusinessId(userId, businessId))
            .thenReturn(Optional.of(active));

        assertThat(service().require(userId, businessId)).isSameAs(active);
    }

    @Test
    void requireRejectsDeactivatedMembership() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        Membership inactive = membership(userId, businessId, false, Instant.now());
        when(membershipRepository.findByUserIdAndBusinessId(userId, businessId))
            .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service().require(userId, businessId))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void requireRejectsMissingMembership() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        when(membershipRepository.findByUserIdAndBusinessId(userId, businessId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().require(userId, businessId))
            .isInstanceOf(ApiException.class);
    }
}
