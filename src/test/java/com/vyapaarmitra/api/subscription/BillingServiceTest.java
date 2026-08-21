package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
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
}
