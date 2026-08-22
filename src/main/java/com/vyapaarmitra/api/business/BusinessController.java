package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BusinessDtos.BusinessResponse;
import com.vyapaarmitra.api.business.BusinessDtos.CreateBusinessRequest;
import com.vyapaarmitra.api.business.BusinessDtos.UpdateBusinessRequest;
import com.vyapaarmitra.api.business.BusinessDtos.UpdateUpiRequest;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
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

    // A UPI VPA looks like handle@bank, e.g. shop123@okhdfcbank. Deliberately permissive
    // on the handle (letters/digits/.-_) and the bank suffix (letters), matching NPCI.
    private static final Pattern VPA_PATTERN =
        Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$");

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final BusinessProvisioningService provisioningService;

    public BusinessController(BusinessRepository businessRepository, UserRepository userRepository,
                              BusinessProvisioningService provisioningService) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
    }

    /** The caller's active business (name + GSTIN). Any member can read it. */
    @GetMapping
    @Transactional(readOnly = true)
    public BusinessResponse get(@AuthenticationPrincipal AuthUser authUser) {
        Business business = businessRepository.findById(authUser.businessId())
            .orElseThrow(() -> ApiException.notFound("Business not found"));
        return BusinessResponse.from(business);
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

    /**
     * Set/clear the shop's UPI collection details (VPA + payee name). Owner or branch
     * manager — staff get 403. A blank VPA clears both fields (turns UPI off).
     */
    @PatchMapping("/upi")
    @PreAuthorize("hasAnyRole('OWNER', 'BRANCH_MANAGER')")
    @Transactional
    public BusinessResponse updateUpi(@AuthenticationPrincipal AuthUser authUser,
                                      @Valid @RequestBody UpdateUpiRequest request) {
        Business business = businessRepository.findById(authUser.businessId())
            .orElseThrow(() -> ApiException.notFound("Business not found"));
        String vpa = request.upiVpa() == null ? "" : request.upiVpa().trim();
        if (vpa.isBlank()) {
            business.setUpiVpa(null);
            business.setUpiPayeeName(null);
        } else {
            if (!VPA_PATTERN.matcher(vpa).matches()) {
                throw ApiException.unprocessable("INVALID_VPA", "Enter a valid UPI ID like name@bank");
            }
            business.setUpiVpa(vpa);
            String payee = request.upiPayeeName() == null ? "" : request.upiPayeeName().trim();
            business.setUpiPayeeName(payee.isBlank() ? null : payee);
        }
        return BusinessResponse.from(businessRepository.save(business));
    }
}
