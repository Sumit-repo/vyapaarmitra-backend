package com.vyapaarmitra.api.accountdeletion;

import com.vyapaarmitra.api.auth.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Freezes an identity that has scheduled account deletion. Keyed on the user's
 * {@code deletion_scheduled_at} (NOT {@code users.active}, which would block re-login and defeat
 * reactivation): if it's set and the request isn't on the allowlist, respond
 * {@code 403 {code:"ACCOUNT_PENDING_DELETION", scheduledAt}}. The allowlist keeps auth, cancel,
 * status and export reachable so the client can route to a reactivation interstitial.
 *
 * <p>Runs after {@link com.vyapaarmitra.api.auth.JwtAuthFilter}, so the principal is already set;
 * it only touches the DB for an authenticated request. Registered as a bean in {@code SecurityConfig}
 * (not a component-scanned {@code @Component}) so web-slice tests don't pull it in. See
 * docs/account-deletion.md.
 */
public class PendingDeletionGuardFilter extends OncePerRequestFilter {

    private final PendingDeletionLookup lookup;

    public PendingDeletionGuardFilter(PendingDeletionLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        UUID userId = authenticatedUserId();
        if (userId != null && !isAllowlisted(request)) {
            Instant scheduledAt = lookup.deletionScheduledAt(userId);
            if (scheduledAt != null) {
                writeForbidden(response, scheduledAt);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private UUID authenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser authUser) {
            return authUser.id();
        }
        return null;
    }

    /**
     * Paths that stay open during the grace window: reactivation, status, export, everything
     * under /auth/**, and logout. Everything else is frozen. Matched against the servlet path.
     */
    private boolean isAllowlisted(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        if (path.equals("/api/v1/auth/logout") || path.equals("/api/v1/logout")) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && path.equals("/api/v1/account/deletion/cancel")) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && path.equals("/api/v1/account/deletion")) {
            return true;
        }
        return HttpMethod.GET.matches(method) && path.equals("/api/v1/account/export");
    }

    private void writeForbidden(HttpServletResponse response, Instant scheduledAt)
        throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"error\":{\"code\":\"ACCOUNT_PENDING_DELETION\","
                + "\"message\":\"This account is scheduled for deletion. Reactivate to continue.\","
                + "\"scheduledAt\":\"" + scheduledAt + "\"}}");
    }
}
