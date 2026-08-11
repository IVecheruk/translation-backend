package com.translatelab.backend.subscription.service;

import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import com.translatelab.backend.payment.provider.SubscriptionCancellationGateway;
import com.translatelab.backend.payment.provider.SubscriptionCancellationGatewayResolver;
import com.translatelab.backend.subscription.entity.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountSubscriptionCancellationServiceTest {

    @Mock private AccountSubscriptionCancellationStateService stateService;
    @Mock private SubscriptionCancellationGatewayResolver resolver;
    @Mock private SubscriptionCancellationGateway gateway;

    private AccountSubscriptionCancellationService service;
    private UUID userId;
    private UUID subscriptionId;
    private Instant effectiveAt;

    @BeforeEach
    void setUp() {
        service = new AccountSubscriptionCancellationService(
                stateService,
                resolver
        );
        userId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        effectiveAt = Instant.parse("2026-09-01T00:00:00Z");
    }

    @Test
    void shouldPersistCancellationOnlyAfterProviderAcceptance() {
        givenPreparation(false);
        given(resolver.resolve("TRIBUTE")).willReturn(gateway);
        given(gateway.requestCancellation("order-1")).willReturn(true);

        var response = service.cancel(userId);

        assertTrue(response.cancelAtPeriodEnd());
        assertEquals(effectiveAt, response.effectiveAt());
        verify(stateService).confirm(userId, subscriptionId);
    }

    @Test
    void shouldNotConfirmRejectedProviderRequest() {
        givenPreparation(false);
        given(resolver.resolve("TRIBUTE")).willReturn(gateway);
        given(gateway.requestCancellation("order-1")).willReturn(false);

        assertThrows(
                PaymentProviderUnavailableException.class,
                () -> service.cancel(userId)
        );
        verify(stateService, org.mockito.Mockito.never()).confirm(
                userId,
                subscriptionId
        );
    }

    @Test
    void shouldReuseAlreadyScheduledCancellationWithoutProviderCall() {
        givenPreparation(true);

        var response = service.cancel(userId);

        assertTrue(response.cancelAtPeriodEnd());
        verifyNoInteractions(resolver, gateway);
    }

    private void givenPreparation(boolean alreadyScheduled) {
        given(stateService.prepare(userId)).willReturn(
                new AccountSubscriptionCancellationStateService
                        .CancellationPreparation(
                        subscriptionId,
                        "TRIBUTE",
                        "order-1",
                        effectiveAt,
                        alreadyScheduled,
                        SubscriptionStatus.ACTIVE
                )
        );
    }
}
