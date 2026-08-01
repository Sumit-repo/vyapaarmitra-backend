package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BusinessDtos.BusinessResponse;
import com.vyapaarmitra.api.business.BusinessDtos.CreateBusinessRequest;
import com.vyapaarmitra.api.business.BusinessDtos.UpdateBusinessRequest;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
public class BusinessController {

    private static final String DEFAULT_BRANCH_NAME = "Main Branch";

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BusinessProvisioningService provisioningService;

    public BusinessController(BusinessRepository businessRepository, UserRepository userRepository,
                              BusinessProvisioningService provisioningService) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
    }

    /**
     * Create another business for the signed-in identity — they become its OWNER. No cap:
     * every new business is a conversion opportunity. The trial is once per person, so the
     * new business trials only if they haven't used it (enforced in provisioning). The
     * client then switches to it via {@code POST /session} (the new membership is listed by
     * {@code GET /memberships}).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessResponse create(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody CreateBusinessRequest request) {
        User owner = userRepository.findById(authUser.id())
            .orElseThrow(() -> ApiException.notFound("Account not found"));
        String branchName = request.branchName() == null || request.branchName().isBlank()
            ? DEFAULT_BRANCH_NAME
            : request.branchName().trim();
        Business business = provisioningService.provisionAdditionalBusiness(
            owner, request.name().trim(), branchName);
        return BusinessResponse.from(business);
    }

    /** Update the shop profile (name / GSTIN). Owner-only; every other role gets 403. */
    @PatchMapping
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public BusinessResponse update(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody UpdateBusinessRequest request) {
        Business business = businessRepository.findById(authUser.businessId())
            .orElseThrow(() -> ApiException.notFound("Business not found"));
        business.setName(request.name().trim());
        if (request.gstin() != null) {
            String g = request.gstin().trim().toUpperCase();
            business.setGstin(g.isBlank() ? null : g);
        }
        return BusinessResponse.from(businessRepository.save(business));
    }
}
