package com.vyapaarmitra.api.telemetry;

import com.vyapaarmitra.api.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relay for mobile telemetry (product funnel + client error events). Authenticated — the
 * signed-in identity is what we attribute events to, and it keeps the endpoint from being an
 * open pipe into our New Relic account. Returns 202 immediately; forwarding is fire-and-forget.
 */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void events(@AuthenticationPrincipal AuthUser user,
                       @Valid @RequestBody TelemetryBatch batch) {
        service.ingest(user, batch);
    }
}
