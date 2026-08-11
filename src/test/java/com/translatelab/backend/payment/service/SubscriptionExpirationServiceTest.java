package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionExpirationCommand;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.exception.UserSubscriptionNotFoundException;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationServiceTest {

    private static final String PROVIDER = "TRIBUTE";
    private static final String EXTERNAL_EVENT_ID = "event-123";
    private static final String EXTERNAL_SUBSCRIPTION_ID =
            "subscription-456";
    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Mock
    private ProcessedPaymentEventRepository paymentEventRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserSubscription subscription;

    @Mock
    private Clock clock;

    private SubscriptionExpirationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionExpirationService(
                paymentEventRepository,
                userSubscriptionRepository,
                clock
        );
    }

    @Test
    void shouldProcessNewExpirationInRequiredOrder() {
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.of(subscription));
        given(clock.instant()).willReturn(NOW);

        boolean processed = service.processExpiration(validCommand());

        InOrder order = inOrder(
                paymentEventRepository,
                userSubscriptionRepository,
                clock,
                subscription
        );
        order.verify(paymentEventRepository).insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_EXPIRED")
        );
        order.verify(userSubscriptionRepository)
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                );
        order.verify(clock).instant();
        order.verify(subscription).expire(NOW);

        assertTrue(processed);
    }

    @Test
    void shouldReturnFalseWithoutLoadingSubscriptionOrClockForDuplicate() {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_EXPIRED")
        )).willReturn(0);

        boolean processed = service.processExpiration(validCommand());

        assertFalse(processed);
        verifyNoInteractions(
                userSubscriptionRepository,
                subscription,
                clock
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void shouldRejectUnexpectedInsertedRowCount(int insertedRows) {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_EXPIRED")
        )).willReturn(insertedRows);

        assertThrows(
                IllegalStateException.class,
                () -> service.processExpiration(validCommand())
        );

        verifyNoInteractions(
                userSubscriptionRepository,
                subscription,
                clock
        );
    }

    @Test
    void shouldThrowWithoutReadingClockWhenSubscriptionDoesNotExist() {
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.empty());

        assertThrows(
                UserSubscriptionNotFoundException.class,
                () -> service.processExpiration(validCommand())
        );

        verifyNoInteractions(subscription, clock);
    }

    @Test
    void shouldPropagateDomainTransitionFailure() {
        IllegalStateException domainFailure = new IllegalStateException(
                "Оплаченный период ещё не завершён"
        );
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.of(subscription));
        given(clock.instant()).willReturn(NOW);
        willThrow(domainFailure).given(subscription).expire(NOW);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.processExpiration(validCommand())
        );

        assertSame(domainFailure, thrown);
    }

    @Test
    void shouldRejectNullCommandBeforeRepositoryCalls() {
        assertThrows(
                NullPointerException.class,
                () -> service.processExpiration(null)
        );

        verifyNoInteractions(
                paymentEventRepository,
                userSubscriptionRepository,
                subscription,
                clock
        );
    }

    private void givenNewEvent() {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_EXPIRED")
        )).willReturn(1);
    }

    private SubscriptionExpirationCommand validCommand() {
        return new SubscriptionExpirationCommand(
                PROVIDER,
                EXTERNAL_EVENT_ID,
                EXTERNAL_SUBSCRIPTION_ID
        );
    }
}
