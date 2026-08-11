package com.translatelab.backend.payment.service;

import com.translatelab.backend.config.PaymentMaintenanceProperties;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRecordMaintenanceServiceTest {

    @Mock private SubscriptionPurchaseIntentRepository intentRepository;
    @Mock private ProcessedPaymentEventRepository eventRepository;
    @Mock private SubscriptionPurchaseIntent expiredIntent;

    @Test
    void shouldExpireAndPurgeOnlyBoundedRetentionBatch() {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        PaymentMaintenanceProperties properties =
                new PaymentMaintenanceProperties(
                        100,
                        Duration.ofDays(90),
                        Duration.ofDays(400)
                );
        PaymentRecordMaintenanceService service =
                new PaymentRecordMaintenanceService(
                        intentRepository,
                        eventRepository,
                        properties,
                        Clock.fixed(now, ZoneOffset.UTC)
                );
        given(intentRepository.findExpiredPendingForUpdate(
                org.mockito.ArgumentMatchers.eq(now),
                any(Pageable.class)
        )).willReturn(List.of(expiredIntent));
        given(intentRepository.deleteTerminalBefore(
                now.minus(Duration.ofDays(90)),
                100
        )).willReturn(7);
        given(eventRepository.deleteProcessedBefore(
                now.minus(Duration.ofDays(400)),
                100
        )).willReturn(3);

        var result = service.maintainBatch();

        assertEquals(1, result.expiredIntents());
        assertEquals(7, result.deletedIntents());
        assertEquals(3, result.deletedEvents());
        verify(expiredIntent).expire(now);
    }
}
