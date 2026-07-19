package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchDtos.BranchResponse;
import com.vyapaarmitra.api.business.BranchDtos.CreateBranchRequest;
import com.vyapaarmitra.api.business.BranchDtos.UpdateBranchRequest;
import com.vyapaarmitra.api.common.ApiException;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchRepository branchRepository;
    private final BranchAccessService branchAccessService;

    public BranchController(BranchRepository branchRepository,
                            BranchAccessService branchAccessService) {
        this.branchRepository = branchRepository;
        this.branchAccessService = branchAccessService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<BranchResponse> list(@AuthenticationPrincipal AuthUser authUser) {
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
            branch.setActive(request.active());
        }
        return BranchResponse.from(branchRepository.save(branch));
    }
}
