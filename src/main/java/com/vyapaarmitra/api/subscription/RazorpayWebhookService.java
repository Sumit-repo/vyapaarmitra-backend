package com.vyapaarmitra.api.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies verified Razorpay subscription webhooks to our {@link Subscription} row.
 * This is the ONLY path that turns a subscription ACTIVE — nothing else grants a
 * paid plan. Idempotent via {@link BillingEvent}: a replayed event is a no-op.
 */
@Slf4j
@Service
public class RazorpayWebhookService {

    private static final String GATEWAY = "RAZORPAY";
    private static final int DUNNING_GRACE_DAYS = 7;

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

        JsonNode entity = root.path("payload").path("subscription").path("entity");
        String subId = entity.path("id").asText(null);
        if (subId != null) {
            subscriptionRepository.findByGatewaySubId(subId)
                .ifPresent(sub -> apply(sub, eventType, entity));
        } else {
            log.warn("Razorpay webhook {} carried no subscription id", eventType);
        }

        event.setProcessedAt(Instant.now());
        billingEventRepository.save(event);
    }

    private void apply(Subscription sub, String eventType, JsonNode entity) {
        switch (eventType) {
            case "subscription.activated", "subscription.charged" -> {
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setGraceUntil(null);
                Instant periodEnd = epochSeconds(entity.path("current_end").asLong(0));
                if (periodEnd != null) {
                    sub.setCurrentPeriodEnd(periodEnd);
                }
            }
            case "subscription.pending", "subscription.halted" -> {
                sub.setStatus(SubscriptionStatus.PAST_DUE);
                sub.setGraceUntil(Instant.now().plus(DUNNING_GRACE_DAYS, ChronoUnit.DAYS));
            }
            case "subscription.cancelled" -> sub.setStatus(SubscriptionStatus.CANCELLED);
            case "subscription.completed" -> sub.setStatus(SubscriptionStatus.EXPIRED);
            default -> log.debug("Ignoring unhandled Razorpay event {}", eventType);
        }
        subscriptionRepository.save(sub);
    }

    private static Instant epochSeconds(long seconds) {
        return seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
    }
}
