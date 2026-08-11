package com.translatelab.backend.payment.service;

import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationReconciliationServiceTest {

    @Mock private UserSubscriptionRepository repository;
    @Mock private UserSubscription first;
    @Mock private UserSubscription second;

    @Test
    void shouldExpireBoundedBatchAtOneClockInstant() {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        given(repository.findExpiredLiveForUpdate(
                org.mockito.ArgumentMatchers.eq(now),
                any(Pageable.class)
        )).willReturn(List.of(first, second));
        var service = new SubscriptionExpirationReconciliationService(
                repository,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertEquals(2, service.reconcileBatch(100));
        verify(first).expire(now);
        verify(second).expire(now);
    }
}
