package com.vyapaarmitra.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionMaintenanceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionMaintenance maintenance;

    @Test
    void expiresLapsedTrialsWithCurrentInstant() {
        when(subscriptionRepository.expireLapsedTrials(org.mockito.ArgumentMatchers.any())).thenReturn(3);

        Instant before = Instant.now();
        maintenance.expireLapsedTrials();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(subscriptionRepository).expireLapsedTrials(cutoff.capture());
        // Uses "now" as the cutoff so only fully-lapsed trials are flipped.
        assertThat(cutoff.getValue()).isBetween(before, after);
    }
}
