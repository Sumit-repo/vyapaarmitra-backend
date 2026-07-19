package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                if (JwtService.TOKEN_TYPE_ACCESS.equals(claims.get("typ", String.class))) {
                    AuthUser authUser = new AuthUser(
                        UUID.fromString(claims.getSubject()),
                        UUID.fromString(claims.get("bid", String.class)),
                        Role.valueOf(claims.get("role", String.class)));
                    var authentication = new UsernamePasswordAuthenticationToken(
                        authUser, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid token: proceed unauthenticated; the entry point returns 401.
            }
        }
        filterChain.doFilter(request, response);
    }
}
