package com.vyapaarmitra.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.config.AppProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Verifies Google ID tokens by calling Google's public {@code tokeninfo} endpoint,
 * which validates the signature and expiry server-side. We then check the audience
 * against our configured client IDs and require a verified email. This avoids the
 * heavyweight google-api-client dependency and keeps the backend cloud-portable.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final Set<String> VALID_ISSUERS =
        Set.of("accounts.google.com", "https://accounts.google.com");

    private final List<String> allowedClientIds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GoogleTokenVerifier(AppProperties props, ObjectMapper objectMapper) {
        this.allowedClientIds = props.google() == null || props.google().clientIds() == null
            ? List.of()
            : props.google().clientIds();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /** Verified identity extracted from a Google ID token. */
    public record GoogleIdentity(String sub, String email, String name) {
    }

    public GoogleIdentity verify(String idToken) {
        if (allowedClientIds.isEmpty()) {
            log.error("[google] GOOGLE_CLIENT_IDS not configured — cannot verify tokens");
            throw ApiException.unauthorized("Google sign-in is not configured");
        }
        JsonNode payload = fetchTokenInfo(idToken);

        String aud = payload.path("aud").asText(null);
        if (aud == null || !allowedClientIds.contains(aud)) {
            log.warn("[google] token audience {} not in allowed client IDs", aud);
            throw ApiException.unauthorized("Invalid Google token");
        }
        String iss = payload.path("iss").asText(null);
        if (iss == null || !VALID_ISSUERS.contains(iss)) {
            throw ApiException.unauthorized("Invalid Google token");
        }
        // tokeninfo only returns 200 for unexpired tokens, but double-check exp defensively.
        long exp = payload.path("exp").asLong(0);
        if (exp > 0 && exp < System.currentTimeMillis() / 1000) {
            throw ApiException.unauthorized("Google token has expired");
        }
        boolean emailVerified = payload.path("email_verified").asBoolean(false)
            || "true".equals(payload.path("email_verified").asText());
        String email = payload.path("email").asText(null);
        if (email == null || !emailVerified) {
            throw ApiException.unauthorized("Google account has no verified email");
        }
        String sub = payload.path("sub").asText(null);
        if (sub == null) {
            throw ApiException.unauthorized("Invalid Google token");
        }
        String name = payload.path("name").asText(null);
        return new GoogleIdentity(sub, email.toLowerCase(), name);
    }

    private JsonNode fetchTokenInfo(String idToken) {
        try {
            URI uri = URI.create("https://oauth2.googleapis.com/tokeninfo?id_token="
                + URLEncoder.encode(idToken, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw ApiException.unauthorized("Invalid Google token");
            }
            return objectMapper.readTree(response.body());
        } catch (java.io.IOException e) {
            log.error("[google] tokeninfo request failed", e);
            throw ApiException.unauthorized("Could not verify Google token");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.unauthorized("Could not verify Google token");
        }
    }
}
