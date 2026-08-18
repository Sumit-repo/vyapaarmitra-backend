package com.vyapaarmitra.api.telemetry;

import com.vyapaarmitra.api.auth.AuthUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Turns client telemetry into New Relic log entries (queryable via {@code FROM Log WHERE
 * logtype = 'vmmobile'}). Server-authoritative on identity: userId / businessId / role come
 * from the access token, never the request body. Caller-supplied props are defensively capped
 * (count + value length, scalars only) and can never overwrite a server-set attribute — and
 * the client only ever sends enums/counts, never PII (names, phones, amounts).
 */
@Service
public class TelemetryService {

    private static final int MAX_PROPS = 30;
    private static final int MAX_VALUE_LEN = 300;

    private final NewRelicClient newRelic;

    public TelemetryService(NewRelicClient newRelic) {
        this.newRelic = newRelic;
    }

    public void ingest(AuthUser user, TelemetryBatch batch) {
        List<Map<String, Object>> logs = batch.events().stream()
            .map(e -> toLogEntry(user, batch, e))
            .toList();
        newRelic.send(logs);
    }

    private Map<String, Object> toLogEntry(AuthUser user, TelemetryBatch batch, TelemetryBatch.Event e) {
        Map<String, Object> m = new HashMap<>();
        // `message` is the Log API's primary field; `event` is the queryable attribute NRQL facets on.
        m.put("message", e.name());
        m.put("event", e.name());
        m.put("timestamp", e.ts() != null ? e.ts() : System.currentTimeMillis());

        // Identity from the token — the client never sends (or can spoof) this.
        if (user != null) {
            m.put("userId", user.id().toString());
            if (user.businessId() != null) {
                m.put("businessId", user.businessId().toString());
            }
            if (user.role() != null) {
                m.put("role", user.role().name());
            }
        }

        putIfPresent(m, "sessionId", batch.sessionId());
        putIfPresent(m, "corrId", batch.corrId());
        putIfPresent(m, "platform", batch.platform());
        putIfPresent(m, "appVersion", batch.appVersion());

        // Caller props: scalars only, bounded, and never clobber a server-set key.
        Map<String, Object> props = e.props();
        if (props != null) {
            int count = 0;
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (count++ >= MAX_PROPS) {
                    break;
                }
                Object v = entry.getValue();
                if (v == null) {
                    continue;
                }
                if (v instanceof Number || v instanceof Boolean) {
                    m.putIfAbsent(entry.getKey(), v);
                } else {
                    String s = v.toString();
                    m.putIfAbsent(entry.getKey(),
                        s.length() > MAX_VALUE_LEN ? s.substring(0, MAX_VALUE_LEN) : s);
                }
            }
        }
        return m;
    }

    private static void putIfPresent(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isBlank()) {
            m.put(key, value);
        }
    }
}
