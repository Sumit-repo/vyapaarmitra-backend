package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.subscription.PlanDtos.PlanView;
import com.vyapaarmitra.api.subscription.PlanDtos.Usage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanService planService;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private RazorpayProperties razorpayProperties;

    @InjectMocks
    private BillingService billingService;

    @Test
    void checkoutReferenceIdStaysWithinRazorpayLimit() {
        when(razorpayProperties.isConfigured()).thenReturn(true);
        when(planService.getOrCreate(any())).thenReturn(new Subscription());
        when(razorpayClient.createPaymentLink(anyLong(), any(), any(), any()))
            .thenReturn(new RazorpayClient.RazorpayPaymentLink("plink_1", "https://rzp.io/x", "created"));

        AuthUser user = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), null);
        billingService.checkout(user, PlanTier.LITE, BillingPeriod.MONTHLY);

        // Razorpay rejects a reference_id longer than 40 chars — a full UUID + timestamp overflowed it.
        ArgumentCaptor<String> referenceId = ArgumentCaptor.forClass(String.class);
        verify(razorpayClient).createPaymentLink(anyLong(), any(), any(), referenceId.capture());
        assertThat(referenceId.getValue()).hasSizeLessThanOrEqualTo(40);
    }

    // ---- verify (the pull-based grant the thanks screen / app call on return) ----

    private Subscription subWithPending(UUID businessId) {
        Subscription sub = new Subscription();
        sub.setBusinessId(businessId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPlan(PlanTier.LITE);
        sub.setBillingPeriod(BillingPeriod.MONTHLY);
        sub.setCurrentPeriodEnd(Instant.now().plus(10, ChronoUnit.DAYS));
        sub.setGateway("RAZORPAY");
        sub.setGatewaySubId("plink_1");
        sub.setPendingPlan(PlanTier.PRO);
        sub.setPendingBillingPeriod(BillingPeriod.YEARLY);
        return sub;
    }

    @Test
    void verifyGrantsWhenGatewayReportsTheLinkPaid() {
        when(razorpayProperties.isConfigured()).thenReturn(true);
        UUID businessId = UUID.randomUUID();
        Subscription sub = subWithPending(businessId);
        when(subscriptionRepository.findWithLockByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(razorpayClient.fetchPaymentLink("plink_1"))
            .thenReturn(new RazorpayClient.RazorpayPaymentLinkStatus("plink_1", "paid", 99_900));
        when(planService.view(businessId)).thenReturn(new PlanView(PlanTier.PRO, PlanTier.PRO,
            false, 0, new Usage(2, 1), BillingPeriod.YEARLY, Instant.now().plus(12, ChronoUnit.DAYS), false));

        // Capture BEFORE verify mutates it.
        Instant existingEnd = sub.getCurrentPeriodEnd();
        Instant before = Instant.now();
        BillingDtos.VerifyResponse res = billingService.verify(
            new AuthUser(UUID.randomUUID(), businessId, null));

        assertThat(res.status()).isEqualTo("ACTIVATED");
        assertThat(res.plan().plan()).isEqualTo(PlanTier.PRO);
        // Same grant as the webhook: PRO stacked 1y onto the 10 remaining days.
        assertThat(sub.getCurrentPeriodEnd())
            .isEqualTo(existingEnd.atZone(ZoneOffset.UTC).plusMonths(12).toInstant())
            .isAfter(before.plus(370, ChronoUnit.DAYS));
        assertThat(sub.getPendingPlan()).isNull();
        verify(subscriptionRepository).save(sub);
    }

    @Test
    void verifyReportsPendingWhileTheLinkIsUnpaid() {
        when(razorpayProperties.isConfigured()).thenReturn(true);
        UUID businessId = UUID.randomUUID();
        Subscription sub = subWithPending(businessId);
        when(subscriptionRepository.findWithLockByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(razorpayClient.fetchPaymentLink("plink_1"))
            .thenReturn(new RazorpayClient.RazorpayPaymentLinkStatus("plink_1", "created", 99_900));
        when(planService.view(businessId)).thenReturn(new PlanView(PlanTier.LITE, PlanTier.LITE,
            false, 0, new Usage(0, 0), BillingPeriod.MONTHLY, Instant.now().plus(10, ChronoUnit.DAYS), false));

        BillingDtos.VerifyResponse res = billingService.verify(
            new AuthUser(UUID.randomUUID(), businessId, null));

        assertThat(res.status()).isEqualTo("PENDING");
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void verifyReportsAlreadyActiveWhenTheWebhookAppliedFirst() {
        when(razorpayProperties.isConfigured()).thenReturn(true);
        UUID businessId = UUID.randomUUID();
        // The webhook granted and cleared the pending — verify must not double-stack.
        Subscription sub = new Subscription();
        sub.setBusinessId(businessId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPlan(PlanTier.PRO);
        sub.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        when(subscriptionRepository.findWithLockByBusinessId(businessId)).thenReturn(Optional.of(sub));
        when(planService.view(businessId)).thenReturn(new PlanView(PlanTier.PRO, PlanTier.PRO,
            false, 0, new Usage(0, 0), BillingPeriod.MONTHLY, Instant.now().plus(30, ChronoUnit.DAYS), false));

        BillingDtos.VerifyResponse res = billingService.verify(
            new AuthUser(UUID.randomUUID(), businessId, null));

        assertThat(res.status()).isEqualTo("ALREADY_ACTIVE");
        verifyNoInteractions(razorpayClient);
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void verifyIsRejectedWhenBillingIsDisabled() {
        when(razorpayProperties.isConfigured()).thenReturn(false);

        AuthUser user = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), null);
        assertThatThrownBy(() -> billingService.verify(user))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.getCode()).isEqualTo("BILLING_DISABLED"));
    }
}
