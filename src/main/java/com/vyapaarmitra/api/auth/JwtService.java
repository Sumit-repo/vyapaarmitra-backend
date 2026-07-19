package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.config.AppProperties;
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

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("typ", TOKEN_TYPE_ACCESS)
            .claim("bid", user.getBusinessId().toString())
            .claim("role", user.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofMinutes(jwtProps.accessTtlMinutes()))))
            .signWith(key)
            .compact();
    }

    public String createRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("typ", TOKEN_TYPE_REFRESH)
            .claim("ver", user.getTokenVersion())
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
