package com.vyapaarmitra.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Razorpay subscription webhooks. This endpoint is public (Razorpay can't
 * carry our JWT), so trust comes solely from the HMAC signature over the raw body —
 * reject anything that doesn't verify. On success we always return 2xx so Razorpay
 * stops retrying, even for events we don't act on.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
public class RazorpayWebhookController {

    private final RazorpayProperties props;
    private final RazorpayWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public RazorpayWebhookController(RazorpayProperties props,
                                     RazorpayWebhookService webhookService,
                                     ObjectMapper objectMapper) {
        this.props = props;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Void> razorpay(@RequestBody byte[] rawBody,
                                         @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                         @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        if (!RazorpaySignature.verify(rawBody, signature, props.webhookSecret())) {
            log.warn("Rejected Razorpay webhook with bad signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(new String(rawBody, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Razorpay webhook body was not valid JSON");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String eventType = root.path("event").asText(null);
        if (eventType == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // Fall back to a stable synthetic id if the header is missing, so idempotency still holds.
        String id = eventId != null ? eventId
            : eventType + ":" + root.path("payload").path("subscription").path("entity").path("id").asText("")
              + ":" + root.path("created_at").asText("");

        webhookService.handle(eventType, id, root, rawBody);
        return ResponseEntity.ok().build();
    }
}
