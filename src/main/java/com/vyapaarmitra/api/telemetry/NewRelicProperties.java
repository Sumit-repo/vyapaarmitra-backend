package com.vyapaarmitra.api.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * New Relic Log API credentials. Server-only: the mobile client posts events to our
 * {@code /api/v1/telemetry} relay and we forward them here as logs, so the ingest key never
 * ships in the app bundle. We use the Log API (not the Event API) because logs get 30-day
 * retention on the base tier vs ~8 days for custom events. Blank key = telemetry disabled.
 *
 * @param accountId  New Relic account id (kept for reference / dashboards; not in the Log URL)
 * @param ingestKey  license/ingest key — the account is resolved from the key, not the URL
 * @param endpoint   optional Log API override (EU: https://log-api.eu.newrelic.com/log/v1)
 */
@ConfigurationProperties(prefix = "app.newrelic")
public record NewRelicProperties(String accountId, String ingestKey, String endpoint) {

    public boolean isConfigured() {
        // The Log API resolves the account from the key, so only the key is required.
        return ingestKey != null && !ingestKey.isBlank();
    }

    /** New Relic Log API endpoint (US collector unless overridden for EU). */
    public String logEndpoint() {
        return (endpoint == null || endpoint.isBlank())
            ? "https://log-api.newrelic.com/log/v1"
            : endpoint.replaceAll("/+$", "");
    }
}
