package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.MeResponse;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.user.User;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Single place that turns an authenticated {@link User} into an access/refresh
 * token pair + session payload. Shared by password login, OTP, and Google sign-in
 * so the three auth paths never drift.
 */
@Component
public class TokenIssuer {

    private final JwtService jwtService;
    private final BusinessRepository businessRepository;

    public TokenIssuer(JwtService jwtService, BusinessRepository businessRepository) {
        this.jwtService = jwtService;
        this.businessRepository = businessRepository;
    }

    public TokenResponse issue(User user) {
        return new TokenResponse(
            jwtService.createAccessToken(user),
            jwtService.createRefreshToken(user),
            toMe(user));
    }

    public MeResponse toMe(User user) {
        String businessName = businessRepository.findById(user.getBusinessId())
            .map(Business::getName)
            .orElse(null);
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), businessName,
            user.getRole(), user.getBusinessId(), Set.copyOf(user.getBranchIds()));
    }
}
