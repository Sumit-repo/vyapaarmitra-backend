package com.vyapaarmitra.api.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Relays custom events to New Relic's Event API using the JDK HttpClient (no extra Maven
 * dependency, mirrors {@code EmailSender}). Fire-and-forget by design: telemetry must never
 * slow, fail, or throw into a user request, so {@link #send} is async and swallows errors.
 * When unconfigured (no ingest key) events are dropped with a debug log so dev works without NR.
 */
@Slf4j
@Service
public class NewRelicClient {

    // Boot 4's webmvc starter doesn't expose an ObjectMapper bean, so own one here (as EmailSender does).
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NewRelicProperties props;
    private final HttpClient httpClient;

    public NewRelicClient(NewRelicProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public boolean isEnabled() {
        return props.isConfigured();
    }

    /** Fire-and-forget: relay a batch of already-sanitised event maps to New Relic. */
    public void send(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        if (!isEnabled()) {
            log.debug("[newrelic] NEWRELIC_INGEST_KEY not set — dropping {} event(s)", events.size());
            return;
        }
        final String payload;
        try {
            payload = MAPPER.writeValueAsString(events);
        } catch (Exception e) {
            log.warn("[newrelic] could not serialise {} event(s)", events.size(), e);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.eventEndpoint()))
            .timeout(Duration.ofSeconds(10))
            .header("Api-Key", props.ingestKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(res -> {
                if (res.statusCode() / 100 != 2) {
                    log.warn("[newrelic] Event API returned {}: {}", res.statusCode(), res.body());
                }
            })
            .exceptionally(err -> {
                log.warn("[newrelic] event send failed: {}", err.getMessage());
                return null;
            });
    }
}
