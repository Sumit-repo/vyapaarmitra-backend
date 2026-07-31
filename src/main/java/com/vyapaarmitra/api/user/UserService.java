package com.vyapaarmitra.api.user;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.UserDtos.CreateUserRequest;
import com.vyapaarmitra.api.user.UserDtos.UpdateUserRequest;
import com.vyapaarmitra.api.user.UserDtos.UserResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, MembershipRepository membershipRepository,
                       BranchRepository branchRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(AuthUser authUser) {
        return membershipRepository.findByBusinessIdOrderByCreatedAtAsc(authUser.businessId()).stream()
            .map(membership -> UserResponse.from(loadUser(membership.getUserId()), membership))
            .toList();
    }

    /**
     * Add someone to this business. If their email already exists as an identity —
     * e.g. they work at another shop — we attach a new membership rather than block
     * them (their login and other memberships are untouched). A brand-new email
     * creates the identity first.
     */
    @Transactional
    public UserResponse create(AuthUser authUser, CreateUserRequest request) {
        Set<UUID> branchIds = validatedBranchIds(authUser, request.branchIds());
        if (request.role() != Role.OWNER && branchIds.isEmpty()) {
            throw ApiException.badRequest("BRANCHES_REQUIRED",
                "Managers and staff need at least one branch");
        }

        String email = request.email().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setPhone(request.phone().trim());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFullName(request.fullName().trim());
            // Legacy columns (dropped in a later phase); the membership is the source of truth.
            user.setBusinessId(authUser.businessId());
            user.setRole(request.role());
            userRepository.save(user);
        } else if (membershipRepository.findByUserIdAndBusinessId(user.getId(), authUser.businessId())
                .isPresent()) {
            throw ApiException.badRequest("ALREADY_MEMBER",
                "This person is already part of this business");
        }

        Membership membership = new Membership();
        membership.setUserId(user.getId());
        membership.setBusinessId(authUser.businessId());
        membership.setRole(request.role());
        membership.setBranchIds(branchIds);
        membershipRepository.save(membership);
        return UserResponse.from(user, membership);
    }

    @Transactional
    public UserResponse update(AuthUser authUser, UUID userId, UpdateUserRequest request) {
        Membership membership = membershipRepository
            .findByUserIdAndBusinessId(userId, authUser.businessId())
            .orElseThrow(() -> ApiException.notFound("User not found"));
        User user = loadUser(userId);

        // Identity-level fields live on the person.
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            // Password change revokes every session of this identity (all businesses).
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        // Business-scoped fields live on the membership.
        if (request.role() != null) {
            membership.setRole(request.role());
        }
        if (request.branchIds() != null) {
            membership.setBranchIds(validatedBranchIds(authUser, request.branchIds()));
        }
        if (request.active() != null) {
            if (!request.active() && membership.getRole() == Role.OWNER
                && isLastActiveOwner(authUser.businessId(), membership.getId())) {
                throw ApiException.badRequest("LAST_OWNER",
                    "A business must keep at least one active owner");
            }
            // Business-scoped revoke: refresh is gated on membership.active, so no
            // identity-wide token bump — the person keeps access to their other shops.
            membership.setActive(request.active());
        }

        userRepository.save(user);
        membershipRepository.save(membership);
        return UserResponse.from(user, membership);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    /** True when no OTHER active OWNER remains in the business. */
    private boolean isLastActiveOwner(UUID businessId, UUID membershipId) {
        return membershipRepository.findByBusinessIdOrderByCreatedAtAsc(businessId).stream()
            .filter(Membership::isActive)
            .filter(m -> m.getRole() == Role.OWNER)
            .noneMatch(m -> !m.getId().equals(membershipId));
    }

    private Set<UUID> validatedBranchIds(AuthUser authUser, Set<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            return new HashSet<>();
        }
        Set<UUID> valid = branchRepository.findActiveIdsByBusinessId(authUser.businessId());
        if (!valid.containsAll(requested)) {
            throw ApiException.badRequest("INVALID_BRANCHES",
                "One or more branches do not belong to this business");
        }
        return new HashSet<>(requested);
    }
}
