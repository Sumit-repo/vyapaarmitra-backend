package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchDtos.BranchResponse;
import com.vyapaarmitra.api.business.BranchDtos.CreateBranchRequest;
import com.vyapaarmitra.api.business.BranchDtos.UpdateBranchRequest;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.subscription.PlanGuard;
import com.vyapaarmitra.api.user.Role;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    /** Short cooldown after deactivation before a branch may be reactivated (anti cap-gaming). */
    private static final int REACTIVATION_COOLDOWN_HOURS = 24;

    private final BranchRepository branchRepository;
    private final BranchAccessService branchAccessService;
    private final PlanGuard planGuard;

    public BranchController(BranchRepository branchRepository,
                            BranchAccessService branchAccessService,
                            PlanGuard planGuard) {
        this.branchRepository = branchRepository;
        this.branchAccessService = branchAccessService;
        this.planGuard = planGuard;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<BranchResponse> list(@AuthenticationPrincipal AuthUser authUser,
                                     @RequestParam(defaultValue = "false") boolean includeInactive) {
        // Owners managing branches need to see inactive ones (to reactivate them); everyone
        // else (and the default) sees only the active branches they can operate in.
        if (includeInactive && authUser.role() == Role.OWNER) {
            return branchRepository.findByBusinessIdOrderByCreatedAtAsc(authUser.businessId()).stream()
                .map(BranchResponse::from)
                .toList();
        }
        Set<UUID> accessible = branchAccessService.accessibleBranchIds(authUser);
        return branchRepository.findByBusinessIdOrderByCreatedAtAsc(authUser.businessId()).stream()
            .filter(branch -> accessible.contains(branch.getId()))
            .map(BranchResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public BranchResponse create(@AuthenticationPrincipal AuthUser authUser,
                                 @Valid @RequestBody CreateBranchRequest request) {
        planGuard.assertCanAddBranch(authUser, branchRepository.countByBusinessIdAndActiveTrue(authUser.businessId()));
        Branch branch = new Branch();
        branch.setBusinessId(authUser.businessId());
        branch.setName(request.name());
        branch.setAddress(request.address());
        return BranchResponse.from(branchRepository.save(branch));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public BranchResponse update(@AuthenticationPrincipal AuthUser authUser,
                                 @PathVariable UUID id,
                                 @RequestBody UpdateBranchRequest request) {
        Branch branch = branchRepository.findById(id)
            .filter(b -> b.getBusinessId().equals(authUser.businessId()))
            .orElseThrow(() -> ApiException.notFound("Branch not found"));
        if (request.name() != null) {
            branch.setName(request.name());
        }
        if (request.address() != null) {
            branch.setAddress(request.address());
        }
        if (request.active() != null) {
            applyActiveChange(authUser, branch, request.active());
        }
        return BranchResponse.from(branchRepository.save(branch));
    }

    /**
     * Toggle a branch's active flag with guards:
     *  - deactivating stamps {@code deactivatedAt} (starts the cooldown);
     *  - reactivating is blocked during the {@link #REACTIVATION_COOLDOWN_HOURS} cooldown and
     *    must fit within the plan's branch cap (counting only active branches), so a parked
     *    branch can't be flipped back on to exceed the limit.
     */
    private void applyActiveChange(AuthUser authUser, Branch branch, boolean nextActive) {
        if (nextActive == branch.isActive()) {
            return; // no-op
        }
        if (!nextActive) {
            branch.setActive(false);
            branch.setDeactivatedAt(Instant.now());
            return;
        }
        // Reactivating: enforce the cooldown, then the active-branch cap.
        if (branch.getDeactivatedAt() != null) {
            Instant availableAt = branch.getDeactivatedAt().plus(REACTIVATION_COOLDOWN_HOURS, ChronoUnit.HOURS);
            if (Instant.now().isBefore(availableAt)) {
                throw ApiException.badRequest("BRANCH_COOLDOWN",
                    "This branch was just deactivated — you can reactivate it after a short cooldown.");
            }
        }
        planGuard.assertCanAddBranch(authUser,
            branchRepository.countByBusinessIdAndActiveTrue(authUser.businessId()));
        branch.setActive(true);
        branch.setDeactivatedAt(null);
    }
}
