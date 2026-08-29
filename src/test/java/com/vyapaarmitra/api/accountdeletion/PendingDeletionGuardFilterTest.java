package com.vyapaarmitra.api.accountdeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.user.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for the freeze guard. The filter is a plain class (registered as a bean in
 * SecurityConfig, not component-scanned) so it's tested directly with mocked servlet objects.
 */
class PendingDeletionGuardFilterTest {

    private PendingDeletionLookup lookup;
    private PendingDeletionGuardFilter filter;

    @BeforeEach
    void setUp() {
        lookup = mock(PendingDeletionLookup.class);
        filter = new PendingDeletionGuardFilter(lookup);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID userId) {
        AuthUser authUser = new AuthUser(userId, UUID.randomUUID(), Role.OWNER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            authUser, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }

    private HttpServletResponse response() throws Exception {
        HttpServletResponse res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(mock(java.io.PrintWriter.class));
        return res;
    }

    // §11: pending user hits a normal endpoint → 403 {code:"ACCOUNT_PENDING_DELETION", scheduledAt}.
    @Test
    void pendingUserHittingNormalEndpointGets403() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        Instant scheduledAt = Instant.now().plus(20, java.time.temporal.ChronoUnit.DAYS);
        when(lookup.deletionScheduledAt(userId)).thenReturn(scheduledAt);
        HttpServletRequest req = request("GET", "/api/v1/customers");
        HttpServletResponse res = response();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(res).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    // §11: pending user hits an allowlisted endpoint → allowed through.
    @Test
    void pendingUserHittingAllowlistedEndpointIsLetThrough() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        Instant scheduledAt = Instant.now().plus(20, java.time.temporal.ChronoUnit.DAYS);
        when(lookup.deletionScheduledAt(userId)).thenReturn(scheduledAt);
        FilterChain chain = mock(FilterChain.class);

        // cancel, status, export, and /auth/** are all allowlisted.
        for (String uri : new String[]{
            "/api/v1/account/deletion/cancel",  // POST
            "/api/v1/account/deletion",          // GET
            "/api/v1/account/export",            // GET
            "/api/v1/auth/otp/request"           // any method under /auth/**
        }) {
            String method = uri.equals("/api/v1/account/deletion/cancel") ? "POST" : "GET";
            HttpServletRequest req = request(method, uri);
            HttpServletResponse res = response();
            filter.doFilterInternal(req, res, chain);
            verify(chain).doFilter(req, res);
            verify(res, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    // §11: requesting an OTP while already pending is NOT allowlisted (/otp is a normal path) → 403.
    @Test
    void requestingOtpWhilePendingIsBlocked() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        when(lookup.deletionScheduledAt(userId))
            .thenReturn(Instant.now().plus(10, java.time.temporal.ChronoUnit.DAYS));
        HttpServletRequest req = request("POST", "/api/v1/account/deletion/otp");
        HttpServletResponse res = response();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(res).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void activeUserPassesThroughUnchanged() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        when(lookup.deletionScheduledAt(userId)).thenReturn(null);
        HttpServletRequest req = request("GET", "/api/v1/customers");
        HttpServletResponse res = response();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(res, never()).setStatus(any(int.class));
    }

    @Test
    void unauthenticatedRequestPassesThrough() throws Exception {
        SecurityContextHolder.clearContext();
        HttpServletRequest req = request("GET", "/api/v1/customers");
        HttpServletResponse res = response();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(lookup, never()).deletionScheduledAt(any());
    }
}