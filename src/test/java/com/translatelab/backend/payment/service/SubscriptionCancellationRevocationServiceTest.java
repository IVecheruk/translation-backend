package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionCancellationRevocationCommand;
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
class SubscriptionCancellationRevocationServiceTest {

    private static final String PROVIDER = "TRIBUTE";
    private static final String EXTERNAL_EVENT_ID = "event-123";
    private static final String EXTERNAL_SUBSCRIPTION_ID =
            "subscription-456";

    @Mock
    private ProcessedPaymentEventRepository paymentEventRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserSubscription subscription;

    private SubscriptionCancellationRevocationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionCancellationRevocationService(
                paymentEventRepository,
                userSubscriptionRepository
        );
    }

    @Test
    void shouldProcessNewRevocationInRequiredOrder() {
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.of(subscription));

        boolean processed = service.processCancellationRevocation(
                validCommand()
        );

        InOrder order = inOrder(
                paymentEventRepository,
                userSubscriptionRepository,
                subscription
        );
        order.verify(paymentEventRepository).insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_CANCELLATION_REVOKED")
        );
        order.verify(userSubscriptionRepository)
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                );
        order.verify(subscription).revokeCancellationAtPeriodEnd();

        assertTrue(processed);
    }

    @Test
    void shouldReturnFalseWithoutLoadingSubscriptionForDuplicate() {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_CANCELLATION_REVOKED")
        )).willReturn(0);

        boolean processed = service.processCancellationRevocation(
                validCommand()
        );

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
                eq("SUBSCRIPTION_CANCELLATION_REVOKED")
        )).willReturn(insertedRows);

        assertThrows(
                IllegalStateException.class,
                () -> service.processCancellationRevocation(validCommand())
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
                () -> service.processCancellationRevocation(validCommand())
        );

        verifyNoInteractions(subscription);
    }

    @Test
    void shouldPropagateDomainTransitionFailure() {
        IllegalStateException domainFailure = new IllegalStateException(
                "Подписка не активна"
        );
        givenNewEvent();
        given(userSubscriptionRepository
                .findByProviderAndExternalSubscriptionIdForUpdate(
                        PROVIDER,
                        EXTERNAL_SUBSCRIPTION_ID
                )).willReturn(Optional.of(subscription));
        willThrow(domainFailure).given(subscription)
                .revokeCancellationAtPeriodEnd();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.processCancellationRevocation(validCommand())
        );

        assertSame(domainFailure, thrown);
    }

    @Test
    void shouldRejectNullCommandBeforeRepositoryCalls() {
        assertThrows(
                NullPointerException.class,
                () -> service.processCancellationRevocation(null)
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
                eq("SUBSCRIPTION_CANCELLATION_REVOKED")
        )).willReturn(1);
    }

    private SubscriptionCancellationRevocationCommand validCommand() {
        return new SubscriptionCancellationRevocationCommand(
                PROVIDER,
                EXTERNAL_EVENT_ID,
                EXTERNAL_SUBSCRIPTION_ID
        );
    }
}
