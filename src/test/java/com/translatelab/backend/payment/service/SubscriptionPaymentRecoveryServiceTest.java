package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionPaymentRecoveryCommand;
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
class SubscriptionPaymentRecoveryServiceTest {

    private static final String PROVIDER = "TRIBUTE";
    private static final String EXTERNAL_EVENT_ID = "event-123";
    private static final String EXTERNAL_SUBSCRIPTION_ID =
            "subscription-456";
    private static final Instant NEW_PERIOD_START = Instant.parse(
            "2026-09-10T00:00:00Z"
    );
    private static final Instant NEW_PERIOD_END = Instant.parse(
            "2026-10-10T00:00:00Z"
    );

    @Mock
    private ProcessedPaymentEventRepository paymentEventRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserSubscription subscription;

    private SubscriptionPaymentRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPaymentRecoveryService(
                paymentEventRepository,
                userSubscriptionRepository
        );
    }

    @Test
    void shouldProcessNewRecoveryInRequiredOrder() {
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.of(subscription));

        boolean processed = service.processRecovery(validCommand());

        InOrder order = inOrder(
                paymentEventRepository,
                userSubscriptionRepository,
                subscription
        );
        order.verify(paymentEventRepository).insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_PAYMENT_RECOVERED")
        );
        order.verify(userSubscriptionRepository)
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                );
        order.verify(subscription).recoverAfterPayment(
                NEW_PERIOD_START,
                NEW_PERIOD_END
        );

        assertTrue(processed);
    }

    @Test
    void shouldReturnFalseWithoutLoadingSubscriptionForDuplicate() {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_PAYMENT_RECOVERED")
        )).willReturn(0);

        boolean processed = service.processRecovery(validCommand());

        assertFalse(processed);
        verifyNoInteractions(userSubscriptionRepository, subscription);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void shouldRejectUnexpectedInsertedRowCount(int insertedRows) {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_PAYMENT_RECOVERED")
        )).willReturn(insertedRows);

        assertThrows(
                IllegalStateException.class,
                () -> service.processRecovery(validCommand())
        );

        verifyNoInteractions(userSubscriptionRepository, subscription);
    }

    @Test
    void shouldThrowWhenExternalSubscriptionDoesNotExist() {
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.empty());

        assertThrows(
                UserSubscriptionNotFoundException.class,
                () -> service.processRecovery(validCommand())
        );

        verifyNoInteractions(subscription);
    }

    @Test
    void shouldPropagateDomainTransitionFailure() {
        IllegalStateException domainFailure = new IllegalStateException(
                "Подписка не находится в статусе PAST_DUE"
        );
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.of(subscription));
        willThrow(domainFailure).given(subscription).recoverAfterPayment(
                NEW_PERIOD_START,
                NEW_PERIOD_END
        );

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.processRecovery(validCommand())
        );

        assertSame(domainFailure, thrown);
    }

    @Test
    void shouldRejectNullCommandBeforeRepositoryCalls() {
        assertThrows(
                NullPointerException.class,
                () -> service.processRecovery(null)
        );

        verifyNoInteractions(
                paymentEventRepository,
                userSubscriptionRepository,
                subscription
        );
    }

    private void givenNewEvent() {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_PAYMENT_RECOVERED")
        )).willReturn(1);
    }

    private SubscriptionPaymentRecoveryCommand validCommand() {
        return new SubscriptionPaymentRecoveryCommand(
                PROVIDER,
                EXTERNAL_EVENT_ID,
                EXTERNAL_SUBSCRIPTION_ID,
                NEW_PERIOD_START,
                NEW_PERIOD_END
        );
    }
}
