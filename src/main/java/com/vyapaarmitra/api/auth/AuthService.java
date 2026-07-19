package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.MeResponse;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(email)
            .filter(User::isActive)
            .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return tokenResponse(user);
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
        return tokenResponse(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthUser authUser) {
        User user = userRepository.findById(authUser.id())
            .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        return toMe(user);
    }

    private TokenResponse tokenResponse(User user) {
        return new TokenResponse(
            jwtService.createAccessToken(user),
            jwtService.createRefreshToken(user),
            toMe(user));
    }

    private MeResponse toMe(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
            user.getBusinessId(), Set.copyOf(user.getBranchIds()));
    }
}
