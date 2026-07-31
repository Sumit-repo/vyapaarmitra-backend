package com.vyapaarmitra.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.UserDtos.CreateUserRequest;
import com.vyapaarmitra.api.user.UserDtos.UpdateUserRequest;
import com.vyapaarmitra.api.user.UserDtos.UserResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private final UUID businessId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final AuthUser owner = new AuthUser(UUID.randomUUID(), businessId, Role.OWNER);

    private UserService service() {
        return new UserService(userRepository, membershipRepository, branchRepository, passwordEncoder);
    }

    private void stubBranchesAndSaves() {
        when(branchRepository.findActiveIdsByBusinessId(businessId)).thenReturn(Set.of(branchId));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateUserRequest invite(String email) {
        return new CreateUserRequest(email, "9876543210", "password1", "Staffer",
            Role.STAFF, Set.of(branchId));
    }

    @Test
    void createNewEmailProvisionsIdentityAndMembership() {
        stubBranchesAndSaves();
        when(userRepository.findByEmailIgnoreCase("new@shop.com")).thenReturn(Optional.empty());

        UserResponse res = service().create(owner, invite("new@shop.com"));

        assertThat(res.email()).isEqualTo("new@shop.com");
        assertThat(res.role()).isEqualTo(Role.STAFF);
        verify(userRepository).save(any());
        verify(membershipRepository).save(any());
    }

    @Test
    void createExistingIdentityAttachesMembershipWithoutRecreatingTheIdentity() {
        stubBranchesAndSaves();
        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setEmail("both@shop.com");
        when(userRepository.findByEmailIgnoreCase("both@shop.com")).thenReturn(Optional.of(existing));
        when(membershipRepository.findByUserIdAndBusinessId(existing.getId(), businessId))
            .thenReturn(Optional.empty());

        UserResponse res = service().create(owner, invite("both@shop.com"));

        // Same person, new membership — no "email already in use" wall, no new identity row.
        assertThat(res.id()).isEqualTo(existing.getId());
        verify(userRepository, never()).save(any());
        verify(membershipRepository).save(any());
    }

    @Test
    void createRejectsWhenAlreadyAMemberOfThisBusiness() {
        stubBranchesAndSaves();
        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setEmail("dup@shop.com");
        when(userRepository.findByEmailIgnoreCase("dup@shop.com")).thenReturn(Optional.of(existing));
        when(membershipRepository.findByUserIdAndBusinessId(existing.getId(), businessId))
            .thenReturn(Optional.of(new Membership()));

        assertThatThrownBy(() -> service().create(owner, invite("dup@shop.com")))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("ALREADY_MEMBER"));
    }

    @Test
    void deactivatingTheLastActiveOwnerIsRejected() {
        UUID staffUserId = UUID.randomUUID();
        Membership onlyOwner = new Membership();
        onlyOwner.setId(UUID.randomUUID());
        onlyOwner.setUserId(staffUserId);
        onlyOwner.setBusinessId(businessId);
        onlyOwner.setRole(Role.OWNER);
        onlyOwner.setActive(true);

        when(membershipRepository.findByUserIdAndBusinessId(staffUserId, businessId))
            .thenReturn(Optional.of(onlyOwner));
        when(userRepository.findById(staffUserId)).thenReturn(Optional.of(new User()));
        when(membershipRepository.findByBusinessIdOrderByCreatedAtAsc(businessId))
            .thenReturn(List.of(onlyOwner));

        UpdateUserRequest deactivate = new UpdateUserRequest(null, null, false, null, null);

        assertThatThrownBy(() -> service().update(owner, staffUserId, deactivate))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("LAST_OWNER"));
    }
}
