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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

    /** A verified one-time payment_link.paid webhook carrying the plink id. */
    private JsonNode paid(String linkId) throws Exception {
        String json = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{"
            + "\"id\":\"" + linkId + "\"}}}}";
        return mapper.readTree(json);
    }

    private void newEvent(String eventId) {
        when(billingEventRepository.findByGatewayAndGatewayEventId("RAZORPAY", eventId))
            .thenReturn(Optional.empty());
        when(billingEventRepository.save(any(BillingEvent.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void grantsPlanAndStartsPeriodFromNowWhenNoRemainingTime() throws Exception {
        JsonNode root = paid("plink_1");
        byte[] raw = root.toString().getBytes(StandardCharsets.UTF_8);
        newEvent("evt_1");

        // A trial user (no paid period yet) whose 1-month Pro purchase is now paid.
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setPlan(PlanTier.FREE);
        sub.setPendingPlan(PlanTier.PRO);
        sub.setPendingBillingPeriod(BillingPeriod.MONTHLY);
        when(subscriptionRepository.findWithLockByGatewaySubId("plink_1")).thenReturn(Optional.of(sub));

        Instant before = Instant.now();
        service.handle("payment_link.paid", "evt_1", root, raw);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getPlan()).isEqualTo(PlanTier.PRO);
        assertThat(sub.getPendingPlan()).isNull();
        // ~1 month out from "now".
        assertThat(sub.getCurrentPeriodEnd())
            .isAfter(before.plus(27, ChronoUnit.DAYS))
            .isBefore(before.plus(32, ChronoUnit.DAYS));
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void stacksPurchasedPeriodOntoRemainingTime() throws Exception {
        JsonNode root = paid("plink_2");
        byte[] raw = root.toString().getBytes(StandardCharsets.UTF_8);
        newEvent("evt_2");

        // Active Lite with 10 days left, buys 1 year of Pro → Pro, ending 10 days + 1 year from now.
        Instant existingEnd = Instant.now().plus(10, ChronoUnit.DAYS);
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPlan(PlanTier.LITE);
        sub.setBillingPeriod(BillingPeriod.MONTHLY);
        sub.setCurrentPeriodEnd(existingEnd);
        sub.setPendingPlan(PlanTier.PRO);
        sub.setPendingBillingPeriod(BillingPeriod.YEARLY);
        when(subscriptionRepository.findWithLockByGatewaySubId("plink_2")).thenReturn(Optional.of(sub));

        service.handle("payment_link.paid", "evt_2", root, raw);

        assertThat(sub.getPlan()).isEqualTo(PlanTier.PRO);
        assertThat(sub.getBillingPeriod()).isEqualTo(BillingPeriod.YEARLY);
        assertThat(sub.getPendingPlan()).isNull();
        // Stacked from the existing end, not from now.
        Instant expected = existingEnd.atZone(ZoneOffset.UTC).plusMonths(12).toInstant();
        assertThat(sub.getCurrentPeriodEnd()).isEqualTo(expected);
    }

    @Test
    void ignoresAlreadyProcessedEvent() throws Exception {
        JsonNode root = paid("plink_1");
        byte[] raw = root.toString().getBytes(StandardCharsets.UTF_8);

        BillingEvent processed = new BillingEvent();
        processed.setProcessedAt(Instant.now());
        when(billingEventRepository.findByGatewayAndGatewayEventId("RAZORPAY", "evt_1"))
            .thenReturn(Optional.of(processed));

        service.handle("payment_link.paid", "evt_1", root, raw);

        // No re-application: the subscription is never even looked up.
        verify(subscriptionRepository, never()).findWithLockByGatewaySubId(eq("plink_1"));
        verify(billingEventRepository, never()).save(any(BillingEvent.class));
    }
}
