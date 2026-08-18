package com.vyapaarmitra.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyapaarmitra.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin wrapper over the Razorpay Subscriptions REST API. Uses {@link RestClient}
 * with basic auth (key id + secret) — no SDK, so there's nothing to keep in sync
 * beyond the two calls we actually make: create a subscription (returns a hosted
 * checkout link) and cancel one at cycle end.
 */
@Component
public class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

    private static final String BASE_URL = "https://api.razorpay.com/v1";

    // Boot 4's RestClient converter is Jackson 3 (tools.jackson) and can't build a
    // Jackson 2 JsonNode, so we read the raw String and parse it with our own Jackson
    // 2 mapper — the same convention the rest of the codebase uses.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RazorpayProperties props;
    private final RestClient restClient;

    public RazorpayClient(RazorpayProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public record RazorpayPaymentLink(String id, String shortUrl, String status) {
    }

    /**
     * Creates a one-time hosted payment link (no mandate — nothing auto-renews) and returns
     * its short URL. The shopkeeper pays on Razorpay's hosted page; the {@code payment_link.paid}
     * webhook then extends their access (see {@link RazorpayWebhookService}).
     */
    public RazorpayPaymentLink createPaymentLink(long amountPaise, String description,
                                                 UUID businessId, String referenceId) {
        Map<String, Object> body = Map.of(
            "amount", amountPaise,
            "currency", "INR",
            "description", description,
            "reference_id", referenceId,
            // We drive our own SMS/WhatsApp expiry reminders — don't double-notify from Razorpay.
            "notify", Map.of("sms", false, "email", false),
            "reminder_enable", false,
            "notes", Map.of("businessId", businessId.toString()));
        JsonNode res = post("/payment_links", body);
        return new RazorpayPaymentLink(
            res.path("id").asText(null),
            res.path("short_url").asText(null),
            res.path("status").asText(null));
    }

    private JsonNode post(String path, Object body) {
        try {
            String json = restClient.post()
                .uri(path)
                .header("Authorization", basicAuth())
                .body(body)
                .retrieve()
                .body(String.class);
            return MAPPER.readTree(json);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw gatewayError("POST", path, e);
        }
    }

    /** Logs the real Razorpay failure (status + response body) before returning the safe 502. */
    private ApiException gatewayError(String method, String path, Exception e) {
        if (e instanceof RestClientResponseException rre) {
            log.error("Razorpay {} {} failed: {} — {}", method, path,
                rre.getStatusCode().value(), rre.getResponseBodyAsString());
        } else {
            log.error("Razorpay {} {} failed: {}", method, path, e.toString());
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "GATEWAY_ERROR",
            "Payment gateway is unavailable. Please try again.");
    }

    private String basicAuth() {
        String creds = props.keyId() + ":" + props.keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }
}
