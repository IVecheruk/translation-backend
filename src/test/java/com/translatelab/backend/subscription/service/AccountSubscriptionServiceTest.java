package com.translatelab.backend.subscription.service;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.subscription.dto.AccountSubscriptionResponse;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AccountSubscriptionServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private UserSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;

    private AccountSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new AccountSubscriptionService(
                userRepository,
                subscriptionRepository,
                planRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        given(userRepository.existsById(USER_ID)).willReturn(true);
    }

    @Test
    void shouldReturnPaidStateWithoutProviderIdentifiers() {
        SubscriptionPlan plan = new SubscriptionPlan("PRO", "Pro");
        UserSubscription subscription = UserSubscription.providerManaged(
                new User("paid@example.com", "hash"),
                plan,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                "TRIBUTE",
                "private-customer",
                "private-provider-reference"
        );
        subscription.scheduleCancellationAtPeriodEnd();
        given(subscriptionRepository.findCurrentByUserIdAt(USER_ID, NOW))
                .willReturn(Optional.of(subscription));

        AccountSubscriptionResponse response = service.getCurrent(USER_ID);

        assertEquals("PRO", response.planCode());
        assertEquals("ACTIVE", response.status());
        assertEquals(NOW.plusSeconds(3600), response.currentPeriodEnd());
        assertEquals(true, response.cancelAtPeriodEnd());
    }

    @Test
    void shouldReturnFreeFallbackOutsidePaidPeriod() {
        SubscriptionPlan free = new SubscriptionPlan("FREE", "Бесплатный");
        given(subscriptionRepository.findCurrentByUserIdAt(USER_ID, NOW))
                .willReturn(Optional.empty());
        given(planRepository.findById("FREE")).willReturn(Optional.of(free));

        AccountSubscriptionResponse response = service.getCurrent(USER_ID);

        assertEquals("FREE", response.planCode());
        assertEquals("FREE", response.status());
        assertNull(response.currentPeriodStart());
        assertNull(response.currentPeriodEnd());
        assertFalse(response.cancelAtPeriodEnd());
    }
}
