package com.vyapaarmitra.api.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vyapaarmitra.api.membership.MembershipRepository;
import com.vyapaarmitra.api.subscription.PlanTier;
import com.vyapaarmitra.api.subscription.Subscription;
import com.vyapaarmitra.api.subscription.SubscriptionRepository;
import com.vyapaarmitra.api.subscription.SubscriptionStatus;
import com.vyapaarmitra.api.template.MessageTemplateRepository;
import com.vyapaarmitra.api.user.User;
import com.vyapaarmitra.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The trial-once-per-identity guard on {@code provisionAdditionalBusiness}. */
@ExtendWith(MockitoExtension.class)
class BusinessProvisioningServiceTest {

    @Mock private BusinessRepository businessRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MessageTemplateRepository templateRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private BusinessProvisioningService service;

    @Test
    void firstBusinessGetsTrialAndConsumesTheIdentityTrial() {
        User owner = new User(); // trialUsed defaults false

        service.provisionAdditionalBusiness(owner, "Sharma Store", "Main Branch");

        ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(cap.capture());
        Subscription sub = cap.getValue();
        assertThat(sub.getTrialEndsAt()).isNotNull();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(owner.isTrialUsed()).isTrue();
        verify(userRepository).save(owner);
    }

    @Test
    void secondBusinessStartsFreeWithNoTrial() {
        User owner = new User();
        owner.setTrialUsed(true); // already used their one trial

        service.provisionAdditionalBusiness(owner, "Second Shop", "Main Branch");

        ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(cap.capture());
        Subscription sub = cap.getValue();
        assertThat(sub.getTrialEndsAt()).isNull();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(sub.getPlan()).isEqualTo(PlanTier.FREE);
        verify(userRepository, never()).save(owner);
    }
}
