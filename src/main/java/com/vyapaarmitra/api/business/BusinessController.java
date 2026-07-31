package com.vyapaarmitra.api.business;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BusinessDtos.BusinessResponse;
import com.vyapaarmitra.api.business.BusinessDtos.UpdateBusinessRequest;
import com.vyapaarmitra.api.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
public class BusinessController {

    private final BusinessRepository businessRepository;

    public BusinessController(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    /** Rename the shop. Owner-only; every other role gets 403. */
    @PatchMapping
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public BusinessResponse update(@AuthenticationPrincipal AuthUser authUser,
                                   @Valid @RequestBody UpdateBusinessRequest request) {
        Business business = businessRepository.findById(authUser.businessId())
            .orElseThrow(() -> ApiException.notFound("Business not found"));
        business.setName(request.name().trim());
        return BusinessResponse.from(businessRepository.save(business));
    }
}
