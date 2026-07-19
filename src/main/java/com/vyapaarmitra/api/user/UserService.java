package com.vyapaarmitra.api.user;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.common.ApiException;
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
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BranchRepository branchRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(AuthUser authUser) {
        return userRepository.findByBusinessIdOrderByCreatedAtAsc(authUser.businessId()).stream()
            .map(UserResponse::from)
            .toList();
    }

    @Transactional
    public UserResponse create(AuthUser authUser, CreateUserRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(existing -> {
            throw ApiException.badRequest("EMAIL_TAKEN", "A user with this email already exists");
        });
        Set<UUID> branchIds = validatedBranchIds(authUser, request.branchIds());
        if (request.role() != Role.OWNER && branchIds.isEmpty()) {
            throw ApiException.badRequest("BRANCHES_REQUIRED",
                "Managers and staff need at least one branch");
        }
        User user = new User();
        user.setBusinessId(authUser.businessId());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setBranchIds(branchIds);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(AuthUser authUser, UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .filter(u -> u.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("User not found"));
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
            if (!request.active()) {
                // revoke outstanding refresh tokens
                user.setTokenVersion(user.getTokenVersion() + 1);
            }
        }
        if (request.branchIds() != null) {
            user.setBranchIds(validatedBranchIds(authUser, request.branchIds()));
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        return UserResponse.from(userRepository.save(user));
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
