package com.vyapaarmitra.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyapaarmitra.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin wrapper over the Razorpay Subscriptions REST API. Uses {@link RestClient}
 * with basic auth (key id + secret) — no SDK, so there's nothing to keep in sync
 * beyond the two calls we actually make: create a subscription (returns a hosted
 * checkout link) and cancel one at cycle end.
 */
@Component
public class RazorpayClient {

    private static final String BASE_URL = "https://api.razorpay.com/v1";

    private final RazorpayProperties props;
    private final RestClient restClient;

    public RazorpayClient(RazorpayProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public record RazorpaySubscription(String id, String shortUrl, String status) {
    }

    /** Creates a subscription for a plan and returns the hosted checkout link. */
    public RazorpaySubscription createSubscription(String planId, int totalCount, UUID businessId) {
        Map<String, Object> body = Map.of(
            "plan_id", planId,
            "total_count", totalCount,
            "customer_notify", 1,
            "notes", Map.of("businessId", businessId.toString()));
        JsonNode res = post("/subscriptions", body);
        return new RazorpaySubscription(
            res.path("id").asText(null),
            res.path("short_url").asText(null),
            res.path("status").asText(null));
    }

    /** Cancels at the end of the current cycle — the shop keeps access until then. */
    public void cancelAtCycleEnd(String subscriptionId) {
        post("/subscriptions/" + subscriptionId + "/cancel", Map.of("cancel_at_cycle_end", 1));
    }

    private JsonNode post(String path, Object body) {
        try {
            return restClient.post()
                .uri(path)
                .header("Authorization", basicAuth())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GATEWAY_ERROR",
                "Payment gateway is unavailable. Please try again.");
        }
    }

    private String basicAuth() {
        String creds = props.keyId() + ":" + props.keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }
}
