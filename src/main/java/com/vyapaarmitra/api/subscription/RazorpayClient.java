package com.vyapaarmitra.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyapaarmitra.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

    public record RazorpaySubscription(String id, String shortUrl, String status) {
    }

    /**
     * Creates (or reuses) a Razorpay customer carrying the shop's GSTIN, so the tax
     * invoice Razorpay raises for the SaaS charge is GST-valid. Returns the customer id,
     * or null if the gateway couldn't create one (checkout still proceeds without it).
     */
    public String createCustomerWithGstin(String name, String gstin) {
        Map<String, Object> body = Map.of(
            "name", name,
            "gstin", gstin,
            // Return the existing customer instead of erroring if one already matches.
            "fail_existing", 0);
        JsonNode res = post("/customers", body);
        return res.path("id").asText(null);
    }

    /**
     * Creates a subscription for a plan and returns the hosted checkout link.
     * When {@code customerId} is non-null the subscription is tied to that customer
     * (carrying its GSTIN onto the invoice).
     */
    public RazorpaySubscription createSubscription(String planId, int totalCount, UUID businessId,
                                                   String customerId) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
            "plan_id", planId,
            "total_count", totalCount,
            "customer_notify", 1,
            "notes", Map.of("businessId", businessId.toString())));
        if (customerId != null && !customerId.isBlank()) {
            body.put("customer_id", customerId);
        }
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

    public record RazorpayInvoice(String id, String status, long amount, Long issuedAt, String shortUrl) {
    }

    /** Invoices/receipts Razorpay has issued for a subscription (newest first). */
    public List<RazorpayInvoice> listInvoices(String subscriptionId) {
        JsonNode res = get("/invoices?subscription_id=" + subscriptionId + "&count=100");
        List<RazorpayInvoice> out = new ArrayList<>();
        for (JsonNode n : res.path("items")) {
            out.add(new RazorpayInvoice(
                n.path("id").asText(null),
                n.path("status").asText(null),
                n.path("amount").asLong(0),
                n.hasNonNull("issued_at") ? n.path("issued_at").asLong() : null,
                n.path("short_url").asText(null)));
        }
        return out;
    }

    private JsonNode get(String path) {
        try {
            String json = restClient.get()
                .uri(path)
                .header("Authorization", basicAuth())
                .retrieve()
                .body(String.class);
            return MAPPER.readTree(json);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GATEWAY_ERROR",
                "Payment gateway is unavailable. Please try again.");
        }
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GATEWAY_ERROR",
                "Payment gateway is unavailable. Please try again.");
        }
    }

    private String basicAuth() {
        String creds = props.keyId() + ":" + props.keySecret();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }
}
