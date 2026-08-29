package com.vyapaarmitra.api.config;

import com.vyapaarmitra.api.accountdeletion.PendingDeletionGuardFilter;
import com.vyapaarmitra.api.accountdeletion.PendingDeletionLookup;
import com.vyapaarmitra.api.auth.JwtAuthFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                    PendingDeletionGuardFilter pendingDeletionGuardFilter,
                                    CorsConfigurationSource corsConfigurationSource,
                                    RateLimitProperties rateLimitProperties) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .headers(headers -> headers
                // Spring defaults already set nosniff + X-Frame-Options: DENY; add HSTS + referrer policy.
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicy.NO_REFERRER)))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/healthz", "/actuator/health/**", "/api/v1/auth/**").permitAll()
                // Customer share viewer — token + phone-last-4 gated in the service, not by JWT.
                .requestMatchers("/api/v1/public/**").permitAll()
                // Gateway webhooks are authenticated by HMAC signature, not a JWT.
                .requestMatchers("/api/v1/webhooks/**").permitAll()
                // Internal server-to-server endpoints are gated by a shared-secret header, not a JWT.
                .requestMatchers("/api/v1/internal/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}}");
            }))
            // Rate-limit auth endpoints before authentication work happens.
            .addFilterBefore(new RateLimitFilter(rateLimitProperties), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Freeze accounts pending deletion (runs after auth so the principal is set).
            .addFilterAfter(pendingDeletionGuardFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * The freeze guard as a plain bean (not a {@code @Component}) so it's only wired into the real
     * app's filter chain and never pulled into {@code @WebMvcTest} slices.
     */
    @Bean
    PendingDeletionGuardFilter pendingDeletionGuardFilter(PendingDeletionLookup lookup) {
        return new PendingDeletionGuardFilter(lookup);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
