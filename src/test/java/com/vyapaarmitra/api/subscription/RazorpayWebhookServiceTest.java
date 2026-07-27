package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RazorpayWebhookServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingEventRepository billingEventRepository;

    @InjectMocks
    private RazorpayWebhookService service;

    private JsonNode charged(String subId, long currentEnd) throws Exception {
        String json = "{\"event\":\"subscription.charged\",\"payload\":{\"subscription\":{\"entity\":{"
            + "\"id\":\"" + subId + "\",\"current_end\":" + currentEnd + "}}}}";
        return mapper.readTree(json);
    }

    @Test
    void activatesSubscriptionOnCharge() throws Exception {
        long currentEnd = 1_900_000_000L;
        JsonNode root = charged("sub_1", currentEnd);
        byte[] raw = root.toString().getBytes(StandardCharsets.UTF_8);

        when(billingEventRepository.findByGatewayAndGatewayEventId("RAZORPAY", "evt_1"))
            .thenReturn(Optional.empty());
        when(billingEventRepository.save(any(BillingEvent.class))).thenAnswer(i -> i.getArgument(0));

        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setPlan(PlanTier.PRO);
        when(subscriptionRepository.findByGatewaySubId("sub_1")).thenReturn(Optional.of(sub));

        service.handle("subscription.charged", "evt_1", root, raw);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(Instant.ofEpochSecond(currentEnd));
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void ignoresAlreadyProcessedEvent() throws Exception {
        JsonNode root = charged("sub_1", 1_900_000_000L);
        byte[] raw = root.toString().getBytes(StandardCharsets.UTF_8);

        BillingEvent processed = new BillingEvent();
        processed.setProcessedAt(Instant.now());
        when(billingEventRepository.findByGatewayAndGatewayEventId("RAZORPAY", "evt_1"))
            .thenReturn(Optional.of(processed));

        service.handle("subscription.charged", "evt_1", root, raw);

        // No re-application: the subscription is never even looked up.
        verify(subscriptionRepository, never()).findByGatewaySubId(eq("sub_1"));
        verify(billingEventRepository, never()).save(any(BillingEvent.class));
    }
}
