package com.vyapaarmitra.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyapaarmitra.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
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

    /** The subset of a payment link's server-side state {@link BillingService#verify} needs. */
    public record RazorpayPaymentLinkStatus(String id, String status, long amount) {
    }

    /**
     * Creates a one-time hosted payment link (no mandate — nothing auto-renews) and returns
     * its short URL. The shopkeeper pays on Razorpay's hosted page; the {@code payment_link.paid}
     * webhook extends their access (see {@link RazorpayWebhookService}). When a callback
     * base URL is configured, the page also redirects the buyer back to our
     * {@code /billing/thanks} screen after payment, which pulls the grant via
     * {@code POST /billing/verify}.
     */
    public RazorpayPaymentLink createPaymentLink(long amountPaise, String description,
                                                 UUID businessId, String referenceId) {
        // Map.of can't hold conditional entries — build up front.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("description", description);
        body.put("reference_id", referenceId);
        // We drive our own SMS/WhatsApp expiry reminders — don't double-notify from Razorpay.
        body.put("notify", Map.of("sms", false, "email", false));
        body.put("reminder_enable", false);
        body.put("notes", Map.of("businessId", businessId.toString()));
        if (props.callbackBaseUrl() != null && !props.callbackBaseUrl().isBlank()) {
            body.put("callback_url", props.callbackBaseUrl() + "/billing/thanks");
            body.put("callback_method", "get");
        }
        JsonNode res = post("/payment_links", body);
        return new RazorpayPaymentLink(
            res.path("id").asText(null),
            res.path("short_url").asText(null),
            res.path("status").asText(null));
    }

    /**
     * Server-to-server status pull for the caller's payment link — the verify
     * endpoint's source of truth (webhook-independent, so a missed/delayed webhook
     * can't block activation).
     */
    public RazorpayPaymentLinkStatus fetchPaymentLink(String paymentLinkId) {
        JsonNode res = get("/payment_links/" + paymentLinkId);
        return new RazorpayPaymentLinkStatus(
            res.path("id").asText(null),
            res.path("status").asText(null),
            res.path("amount").asLong(0));
    }

    private JsonNode post(String path, Object body) {
        return exchange("POST", () -> restClient.post()
            .uri(path)
            .header("Authorization", basicAuth())
            .body(body)
            .retrieve()
            .body(String.class), path);
    }

    private JsonNode get(String path) {
        return exchange("GET", () -> restClient.get()
            .uri(path)
            .header("Authorization", basicAuth())
            .retrieve()
            .body(String.class), path);
    }

    private JsonNode exchange(String method, java.util.function.Supplier<String> call, String path) {
        try {
            return MAPPER.readTree(call.get());
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw gatewayError(method, path, e);
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
