package com.vyapaarmitra.api.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Embeds a shop's hosted logo into bill PDFs as a data URI, cached per URL. openhtmltopdf
 * would otherwise fetch the Cloudinary image over HTTPS once per render — seconds of
 * latency inside a DB transaction on a cold Cloud Run egress. A data-URI img is already
 * proven in the renderer (the UPI QR embeds as one, and the sample preview's placeholder
 * logo is a data URI).
 *
 * <p>Graceful degradation is the contract: <b>any</b> failure — non-200, non-image
 * content type, oversize, timeout, thrown exception — returns the original https URL
 * unchanged, which is exactly today's behavior (openhtmltopdf then fetches it itself or
 * drops the image). Successes cache for an hour, failures for five minutes, so a dead
 * logo doesn't stall every bill.
 */
@Component
public class LogoDataUri {

    static final Duration SUCCESS_TTL = Duration.ofHours(1);
    static final Duration FAILURE_TTL = Duration.ofMinutes(5);
    /** Refuse to embed anything larger than this — keeps PDFs (and memory) bounded. */
    static final int MAX_BYTES = 512 * 1024;

    private static final String DATA_URI_PREFIX = "data:";

    /** One HTTP GET. Injectable so tests can fake the network. */
    @FunctionalInterface
    public interface Fetcher {
        HttpResponse<byte[]> get(String url) throws IOException, InterruptedException;
    }

    private record Entry(String value, Instant expiresAt) {
        boolean fresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    private final Fetcher fetcher;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public LogoDataUri() {
        this(LogoDataUri::httpGet, Clock.systemUTC());
    }

    /** Test constructor: a fake fetcher plus a mutable clock for the TTL assertions. */
    LogoDataUri(Fetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    /**
     * Returns {@code url} as an embeddable img src: a data URI when the fetch succeeds
     * and the payload is embeddable, the original URL on any failure. Blank/null and
     * non-http URLs pass through untouched.
     */
    public String resolve(String url) {
        if (url == null || url.isBlank() || !url.startsWith("http")) {
            return url;
        }
        Instant now = clock.instant();
        Entry cached = cache.get(url);
        if (cached != null && cached.fresh(now)) {
            return cached.value();
        }
        String resolved = fetch(url);
        cache.put(url, new Entry(resolved, now.plus(resolved.equals(url) ? FAILURE_TTL : SUCCESS_TTL)));
        return resolved;
    }

    private String fetch(String url) {
        try {
            HttpResponse<byte[]> response = fetcher.get(url);
            if (response.statusCode() != 200) {
                return url;
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            byte[] body = response.body();
            if (!contentType.startsWith("image/") || body == null || body.length > MAX_BYTES) {
                return url;
            }
            return DATA_URI_PREFIX + contentType + ";base64,"
                + Base64.getEncoder().encodeToString(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return url;
        } catch (IOException | RuntimeException e) {
            // Includes URI.create's IllegalArgumentException — any failure degrades to the URL.
            return url;
        }
    }

    private static HttpResponse<byte[]> httpGet(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
        return client.send(HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(4))
            .GET()
            .build(), BodyHandlers.ofByteArray());
    }
}