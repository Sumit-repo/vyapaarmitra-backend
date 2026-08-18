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
 * Relays telemetry to New Relic's Log API using the JDK HttpClient (no extra Maven dependency,
 * mirrors {@code EmailSender}). Logs get 30-day retention on the base tier (vs ~8 days for custom
 * events), which is why this posts to the Log API. Fire-and-forget by design: telemetry must never
 * slow, fail, or throw into a user request, so {@link #send} is async and swallows errors. When
 * unconfigured (no ingest key) events are dropped with a debug log so dev works without NR.
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

    /** Fire-and-forget: relay a batch of already-sanitised log entries to New Relic's Log API. */
    public void send(List<Map<String, Object>> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        if (!isEnabled()) {
            log.debug("[newrelic] NEWRELIC_INGEST_KEY not set — dropping {} log(s)", logs.size());
            return;
        }
        // Log API envelope: a common block tags every line so NRQL can filter with
        // `FROM Log WHERE logtype = 'vmmobile'`; the individual lines carry the attributes.
        Map<String, Object> common = Map.of("attributes", Map.of("logtype", "vmmobile"));
        List<Map<String, Object>> body = List.of(Map.of("common", common, "logs", logs));
        final String payload;
        try {
            payload = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("[newrelic] could not serialise {} log(s)", logs.size(), e);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.logEndpoint()))
            .timeout(Duration.ofSeconds(10))
            .header("Api-Key", props.ingestKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(res -> {
                if (res.statusCode() / 100 != 2) {
                    log.warn("[newrelic] Log API returned {}: {}", res.statusCode(), res.body());
                }
            })
            .exceptionally(err -> {
                log.warn("[newrelic] log send failed: {}", err.getMessage());
                return null;
            });
    }
}
