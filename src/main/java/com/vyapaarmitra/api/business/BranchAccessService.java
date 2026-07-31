package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.user.Role;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central branch-level authorization. OWNER sees every active branch in the
 * business; BRANCH_MANAGER and STAFF see only their assigned branches. Every
 * branch-scoped service call goes through here — this is the security boundary
 * that replaces vendor RLS. Branch scope now comes from the acting membership.
 */
@Service
public class BranchAccessService {

    private final BranchRepository branchRepository;
    private final MembershipRepository membershipRepository;

    public BranchAccessService(BranchRepository branchRepository,
                               MembershipRepository membershipRepository) {
        this.branchRepository = branchRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public Set<UUID> accessibleBranchIds(AuthUser authUser) {
        if (authUser.role() == Role.OWNER) {
            return branchRepository.findActiveIdsByBusinessId(authUser.businessId());
        }
        Membership membership = membershipRepository
            .findByUserIdAndBusinessId(authUser.id(), authUser.businessId())
            .filter(Membership::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Session revoked, please log in again"));
        return Set.copyOf(membership.getBranchIds());
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
