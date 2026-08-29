package com.vyapaarmitra.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies verified Razorpay {@code payment_link.paid} webhooks to our {@link Subscription} row.
 * This is the ONLY path that grants paid access — nothing else does. On a paid one-time link we
 * apply the chosen tier and STACK the purchased period onto any remaining time. Idempotent via
 * {@link BillingEvent}: a replayed event is a no-op.
 */
@Slf4j
@Service
public class RazorpayWebhookService {

    private static final String GATEWAY = "RAZORPAY";

    private final SubscriptionRepository subscriptionRepository;
    private final BillingEventRepository billingEventRepository;

    public RazorpayWebhookService(SubscriptionRepository subscriptionRepository,
                                  BillingEventRepository billingEventRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.billingEventRepository = billingEventRepository;
    }

    /** Records the event (once) and applies its effect. Safe to call on retries. */
    @Transactional
    public void handle(String eventType, String eventId, JsonNode root, byte[] rawBody) {
        Optional<BillingEvent> existing =
            billingEventRepository.findByGatewayAndGatewayEventId(GATEWAY, eventId);
        if (existing.isPresent() && existing.get().getProcessedAt() != null) {
            return; // already handled — Razorpay is just retrying
        }

        BillingEvent event = existing.orElseGet(() -> {
            BillingEvent e = new BillingEvent();
            e.setGateway(GATEWAY);
            e.setGatewayEventId(eventId);
            e.setType(eventType);
            e.setPayload(new String(rawBody, StandardCharsets.UTF_8));
            return billingEventRepository.save(e);
        });

        // We only issue one-time payment links now; the paid event carries the plink id.
        JsonNode entity = root.path("payload").path("payment_link").path("entity");
        String linkId = entity.path("id").asText(null);
        if (linkId != null) {
            // Row-locked so a concurrent POST /billing/verify can't apply the same
            // payment a second time (see SubscriptionRepository.findWithLock*).
            subscriptionRepository.findWithLockByGatewaySubId(linkId)
                .ifPresent(sub -> apply(sub, eventType));
        } else {
            log.warn("Razorpay webhook {} carried no payment link id", eventType);
        }

        event.setProcessedAt(Instant.now());
        billingEventRepository.save(event);
    }

    private void apply(Subscription sub, String eventType) {
        if (!"payment_link.paid".equals(eventType)) {
            log.debug("Ignoring unhandled Razorpay event {}", eventType);
            return;
        }
        if (PaymentGrants.grant(sub)) {
            subscriptionRepository.save(sub);
        }
    }
}
