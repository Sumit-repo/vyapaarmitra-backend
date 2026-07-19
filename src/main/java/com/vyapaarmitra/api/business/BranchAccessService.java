package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.user.Role;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central branch-level authorization. OWNER sees every active branch in the
 * business; BRANCH_MANAGER and STAFF see only their assigned branches. Every
 * branch-scoped service call goes through here — this is the security boundary
 * that replaces vendor RLS.
 */
@Service
public class BranchAccessService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public BranchAccessService(BranchRepository branchRepository, UserRepository userRepository) {
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Set<UUID> accessibleBranchIds(AuthUser authUser) {
        if (authUser.role() == Role.OWNER) {
            return branchRepository.findActiveIdsByBusinessId(authUser.businessId());
        }
        User user = userRepository.findById(authUser.id())
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        return Set.copyOf(user.getBranchIds());
    }

    @Transactional(readOnly = true)
    public void assertBranchAccess(AuthUser authUser, UUID branchId) {
        if (!accessibleBranchIds(authUser).contains(branchId)) {
            throw ApiException.forbidden("No access to this branch");
        }
    }

    /**
     * Resolves the branch scope of a request: a specific branch (validated) when
     * branchId is given, otherwise every branch the user can access (consolidated view).
     */
    @Transactional(readOnly = true)
    public Set<UUID> scope(AuthUser authUser, UUID branchId) {
        Set<UUID> accessible = accessibleBranchIds(authUser);
        if (branchId == null) {
            if (accessible.isEmpty()) {
                throw ApiException.forbidden("No branch access assigned");
            }
            return accessible;
        }
        if (!accessible.contains(branchId)) {
            throw ApiException.forbidden("No access to this branch");
        }
        return Set.of(branchId);
    }
}
