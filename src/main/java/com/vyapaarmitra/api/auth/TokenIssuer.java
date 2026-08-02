package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.MeResponse;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.subscription.PlanService;
import com.vyapaarmitra.api.user.User;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Single place that turns an authenticated {@link User} + the {@link Membership}
 * they're acting under into an access/refresh token pair + session payload. Shared
 * by password login, OTP, and Google sign-in so the three auth paths never drift.
 */
@Component
public class TokenIssuer {

    private final JwtService jwtService;
    private final BusinessRepository businessRepository;
    private final PlanService planService;

    public TokenIssuer(JwtService jwtService, BusinessRepository businessRepository,
                       PlanService planService) {
        this.jwtService = jwtService;
        this.businessRepository = businessRepository;
        this.planService = planService;
    }

    public TokenResponse issue(User user, Membership membership) {
        return new TokenResponse(
            jwtService.createAccessToken(user, membership),
            jwtService.createRefreshToken(user, membership),
            toMe(user, membership));
    }

    public MeResponse toMe(User user, Membership membership) {
        String businessName = businessRepository.findById(membership.getBusinessId())
            .map(Business::getName)
            .orElse(null);
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), businessName,
            membership.getRole(), membership.getBusinessId(), Set.copyOf(membership.getBranchIds()),
            user.getDefaultBusinessId(), membership.getPreferredBranchId(),
            planService.view(membership.getBusinessId()));
    }
}
