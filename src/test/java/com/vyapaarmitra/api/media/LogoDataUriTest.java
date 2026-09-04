package com.vyapaarmitra.api.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * LogoDataUri contract: fetch once per URL, embed only a 200 + image/* + ≤512KB payload
 * as a data URI, fall back to the original URL on any failure, and cache that fallback
 * for the short failure TTL so a dead logo doesn't stall every bill.
 */
class LogoDataUriTest {

    private static final String URL = "https://res.cloudinary.com/shop/logo.png";
    private static final byte[] PNG = {1, 2, 3, 4};

    /** Mutable test clock (advance it to test TTL expiry). */
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-05T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }
    }

    /** Fake fetcher: counts calls, serves from a Supplier (or throws). */
    private static final class FakeFetcher implements LogoDataUri.Fetcher {
        final AtomicInteger calls = new AtomicInteger();
        private final Supplier<HttpResponse<byte[]>> response;

        FakeFetcher(Supplier<HttpResponse<byte[]>> response) {
            this.response = response;
        }

        @Override
        public HttpResponse<byte[]> get(String url) {
            calls.incrementAndGet();
            return response.get();
        }
    }

    private static HttpResponse<byte[]> response(int status, String contentType, byte[] body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(URI.create(URL)).build();
            }

            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create(URL);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }

            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public java.net.http.HttpHeaders headers() {
                return java.net.http.HttpHeaders.of(
                    contentType == null ? Map.of() : Map.of("content-type", List.of(contentType)),
                    (k, v) -> true);
            }

            @Override
            public byte[] body() {
                return body;
            }
        };
    }

    private static String dataUriOf(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    @Test
    void embedsA200ImageAsDataUri() {
        MutableClock clock = new MutableClock();
        LogoDataUri logos = new LogoDataUri(new FakeFetcher(() -> response(200, "image/png", PNG)), clock);

        assertThat(logos.resolve(URL)).isEqualTo(dataUriOf(PNG));
    }

    @Test
    void oneFetchServesTwoResolves() {
        MutableClock clock = new MutableClock();
        FakeFetcher fetcher = new FakeFetcher(() -> response(200, "image/png", PNG));
        LogoDataUri logos = new LogoDataUri(fetcher, clock);

        String first = logos.resolve(URL);
        assertThat(logos.resolve(URL)).isEqualTo(first);
        assertThat(fetcher.calls.get()).isEqualTo(1);
    }

    @Test
    void aCachedSuccessExpiresAfterAnHour() {
        MutableClock clock = new MutableClock();
        FakeFetcher fetcher = new FakeFetcher(() -> response(200, "image/png", PNG));
        LogoDataUri logos = new LogoDataUri(fetcher, clock);

        logos.resolve(URL);
        clock.advance(LogoDataUri.SUCCESS_TTL.plusSeconds(1));
        logos.resolve(URL);

        assertThat(fetcher.calls.get()).isEqualTo(2);
    }

    @Test
    void fallsBackToTheOriginalUrlOnNon200() {
        LogoDataUri logos = new LogoDataUri(
            new FakeFetcher(() -> response(404, "image/png", null)), new MutableClock());

        assertThat(logos.resolve(URL)).isEqualTo(URL);
    }

    @Test
    void fallsBackOnANonImageContentType() {
        LogoDataUri logos = new LogoDataUri(
            new FakeFetcher(() -> response(200, "text/html", PNG)), new MutableClock());

        assertThat(logos.resolve(URL)).isEqualTo(URL);
    }

    @Test
    void fallsBackOnAnOversizePayload() {
        LogoDataUri logos = new LogoDataUri(new FakeFetcher(
            () -> response(200, "image/png", new byte[LogoDataUri.MAX_BYTES + 1])), new MutableClock());

        assertThat(logos.resolve(URL)).isEqualTo(URL);
    }

    @Test
    void fallsBackWhenTheFetchThrows() {
        LogoDataUri logos = new LogoDataUri(new FakeFetcher(() -> {
            throw new IllegalStateException("network down");
        }), new MutableClock());

        assertThat(logos.resolve(URL)).isEqualTo(URL);
    }

    @Test
    void aFailureIsNegativelyCachedForTheShortTtl() {
        MutableClock clock = new MutableClock();
        FakeFetcher fetcher = new FakeFetcher(() -> response(404, null, null));
        LogoDataUri logos = new LogoDataUri(fetcher, clock);

        logos.resolve(URL);
        clock.advance(LogoDataUri.FAILURE_TTL.minusSeconds(1));
        logos.resolve(URL);
        assertThat(fetcher.calls.get()).isEqualTo(1); // still cached

        clock.advance(Duration.ofSeconds(2)); // past the failure TTL
        logos.resolve(URL);
        assertThat(fetcher.calls.get()).isEqualTo(2); // retried
    }

    @Test
    void blankAndNonHttpUrlsPassThrough() {
        MutableClock clock = new MutableClock();
        FakeFetcher fetcher = new FakeFetcher(() -> response(200, "image/png", PNG));
        LogoDataUri logos = new LogoDataUri(fetcher, clock);

        assertThat(logos.resolve(null)).isNull();
        assertThat(logos.resolve("  ")).isEqualTo("  ");
        assertThat(logos.resolve("not-a-url")).isEqualTo("not-a-url");
        assertThat(fetcher.calls.get()).isZero();
    }

    @Test
    void theRealClientConstructorHandlesPassthroughWithoutFetching() {
        // No network in unit tests: only the passthrough branch is safe to touch here.
        assertThat(new LogoDataUri().resolve(null)).isNull();
        assertThat(new LogoDataUri().resolve("not-a-url")).isEqualTo("not-a-url");
    }
}