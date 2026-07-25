package com.vyapaarmitra.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate-limit config for the sensitive auth endpoints. Bound from {@code app.rate-limit.*}.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    /** Max auth requests (login/refresh) per client IP per minute. */
    @DefaultValue("20") int authRequestsPerMinute) {
}
