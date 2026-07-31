package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.membership.Membership;
import com.vyapaarmitra.api.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final AppProperties.Jwt jwtProps;

    public JwtService(AppProperties props) {
        this.jwtProps = props.jwt();
        this.key = Keys.hmacShaKeyFor(jwtProps.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The access token is scoped to one active membership: {@code bid}/{@code role}
     * come from the membership (the person's role in that business), not the identity.
     */
    public String createAccessToken(User user, Membership membership) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("typ", TOKEN_TYPE_ACCESS)
            .claim("bid", membership.getBusinessId().toString())
            .claim("role", membership.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofMinutes(jwtProps.accessTtlMinutes()))))
            .signWith(key)
            .compact();
    }

    /**
     * The refresh token carries {@code ver} (identity-level revocation) plus the
     * business it was issued for, so a refresh re-issues for the same business —
     * and fails if that membership has since been deactivated.
     */
    public String createRefreshToken(User user, Membership membership) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("typ", TOKEN_TYPE_REFRESH)
            .claim("ver", user.getTokenVersion())
            .claim("bid", membership.getBusinessId().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofDays(jwtProps.refreshTtlDays()))))
            .signWith(key)
            .compact();
    }

    /** Throws {@link io.jsonwebtoken.JwtException} for invalid/expired tokens. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
