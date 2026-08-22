package com.vyapaarmitra.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In-memory fixed-window rate limiter keyed by client IP. Guards the auth endpoints
 * (login/refresh brute force) and the public share endpoints (phone last-4 guessing —
 * defence-in-depth on top of the per-token lockout in ShareService) without an external
 * dependency. Per-instance; behind a horizontally scaled deployment it caps each instance,
 * which is sufficient at this scale.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final String PUBLIC_PREFIX = "/api/v1/public/";

    private final boolean enabled;
    private final int limitPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties props) {
        this.enabled = props.enabled();
        this.limitPerMinute = Math.max(1, props.authRequestsPerMinute());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith(AUTH_PREFIX) && !uri.startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = clientIp(request);
        Window window = windows.compute(key, (k, existing) ->
            existing != null && existing.minute == minute ? existing : new Window(minute));

        if (window.count.incrementAndGet() > limitPerMinute) {
            tooManyRequests(response);
            return;
        }

        // Opportunistic cleanup so the map can't grow unbounded from unique IPs.
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.minute < minute);
        }

        chain.doFilter(request, response);
    }

    private void tooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please try again in a minute.\"}}");
    }

    /** Prefer the first hop in X-Forwarded-For (Render/Cloud Run) over the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long minute) {
            this.minute = minute;
        }
    }
}
