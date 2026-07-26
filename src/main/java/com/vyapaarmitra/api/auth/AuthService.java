package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.MeResponse;
import com.vyapaarmitra.api.auth.AuthDtos.RegisterRequest;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.business.BusinessProvisioningService;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BusinessProvisioningService provisioningService;
    private final TokenIssuer tokenIssuer;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, BusinessProvisioningService provisioningService,
                       TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.provisioningService = provisioningService;
        this.tokenIssuer = tokenIssuer;
    }

    /** Self-serve signup: stands up a new business and signs the owner straight in. */
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.badRequest("EMAIL_TAKEN", "An account with this email already exists.");
        }
        String branchName = request.branchName() == null || request.branchName().isBlank()
            ? "Main Branch"
            : request.branchName().trim();
        User owner = provisioningService.provision(request.businessName().trim(), branchName,
            request.ownerName().trim(), email, request.password());
        return tokenIssuer.issue(owner);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        // Google-/OTP-only accounts have no password set.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return tokenIssuer.issue(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        if (!JwtService.TOKEN_TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        Integer version = claims.get("ver", Integer.class);
        if (version == null || version != user.getTokenVersion()) {
            throw ApiException.unauthorized("Session revoked, please log in again");
        }
        return tokenIssuer.issue(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        return tokenIssuer.toMe(user);
    }
}
