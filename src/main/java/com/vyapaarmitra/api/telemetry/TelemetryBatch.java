package com.vyapaarmitra.api.telemetry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * A batch of client telemetry events posted by the mobile app. Identity (userId,
 * businessId) is NOT taken from here — it is derived server-side from the access token
 * so the client can never spoof it and never has to send PII.
 *
 * @param sessionId  per-app-launch id (correlates events from one session)
 * @param corrId     correlation id also sent as X-Correlation-Id on API calls
 * @param platform   "ios" | "android"
 * @param appVersion client app version string
 * @param events     the events (bounded so a single request can't flood the relay)
 */
public record TelemetryBatch(
    @Size(max = 64) String sessionId,
    @Size(max = 64) String corrId,
    @Size(max = 16) String platform,
    @Size(max = 32) String appVersion,
    @NotEmpty @Size(max = 50) List<Event> events
) {

    /**
     * @param name  event name, e.g. "paywall_impression", "app_error"
     * @param ts    client epoch millis (server falls back to now if absent)
     * @param props scalar attributes only — callers must never include PII
     */
    public record Event(
        @NotBlank @Size(max = 60) String name,
        Long ts,
        Map<String, Object> props
    ) {}
}
