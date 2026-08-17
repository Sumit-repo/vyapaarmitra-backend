package com.vyapaarmitra.api.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * New Relic Event API credentials. Server-only: the mobile client posts events to our
 * {@code /api/v1/telemetry} relay and we forward them here, so the ingest key never ships
 * in the app bundle. Blank key means telemetry is disabled (events are dropped in dev).
 *
 * @param accountId  New Relic account id (numeric, from the account settings)
 * @param ingestKey  Insights "Insert"/ingest key — write-only to the Event API
 * @param endpoint   optional collector base override (EU: https://insights-collector.eu01.nr-data.net)
 */
@ConfigurationProperties(prefix = "app.newrelic")
public record NewRelicProperties(String accountId, String ingestKey, String endpoint) {

    public boolean isConfigured() {
        return accountId != null && !accountId.isBlank()
            && ingestKey != null && !ingestKey.isBlank();
    }

    /** Full Event API URL for this account (US collector unless overridden). */
    public String eventEndpoint() {
        String base = (endpoint == null || endpoint.isBlank())
            ? "https://insights-collector.newrelic.com"
            : endpoint.replaceAll("/+$", "");
        return base + "/v1/accounts/" + accountId + "/events";
    }
}
