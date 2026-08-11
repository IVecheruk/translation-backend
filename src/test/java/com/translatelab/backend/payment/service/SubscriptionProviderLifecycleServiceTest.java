package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.exception.InvalidPaymentConfirmationException;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.subscription.entity.SubscriptionStatus;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionProviderLifecycleServiceTest {

    @Mock private ProcessedPaymentEventRepository eventRepository;
    @Mock private UserSubscriptionRepository subscriptionRepository;
    @Mock private UserSubscription subscription;

    private SubscriptionProviderLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionProviderLifecycleService(
                eventRepository,
                subscriptionRepository
        );
    }

    @Test
    void shouldRenewValidatedRecurringCharge() {
        Instant periodEnd = Instant.parse("2026-09-01T00:00:00Z");
        givenNewEvent();
        givenSubscription();
        given(subscription.matchesCommercialSnapshot(
                49_900, "RUB", BillingPeriod.MONTH
        )).willReturn(true);
        given(subscription.getCurrentPeriodEnd()).willReturn(periodEnd);

        assertTrue(service.processRecurringCharge(
                "TRIBUTE", "charge:1", "order-1",
                49_900, "RUB", BillingPeriod.MONTH
        ));

        verify(subscription).applySuccessfulRecurringCharge(
                periodEnd,
                Instant.parse("2026-10-01T00:00:00Z")
        );
    }

    @Test
    void shouldIgnoreDuplicateBeforeLoadingSubscription() {
        given(eventRepository.insertIfAbsent(
                any(UUID.class), eq("TRIBUTE"), eq("charge:1"),
                eq("SUBSCRIPTION_CHARGE_SUCCEEDED")
        )).willReturn(0);

        assertFalse(service.processRecurringCharge(
                "TRIBUTE", "charge:1", "order-1",
                49_900, "RUB", BillingPeriod.MONTH
        ));
        verifyNoInteractions(subscriptionRepository, subscription);
    }

    @Test
    void shouldRejectCommercialMismatchBeforeChangingSubscription() {
        givenNewEvent();
        givenSubscription();

        assertThrows(
                InvalidPaymentConfirmationException.class,
                () -> service.processRecurringCharge(
                        "TRIBUTE", "charge:1", "order-1",
                        1, "USD", BillingPeriod.MONTH
                )
        );
        verify(subscription, org.mockito.Mockito.never())
                .applySuccessfulRecurringCharge(any(), any());
    }

    @Test
    void shouldMakeRepeatedFailureStateSafe() {
        given(eventRepository.insertIfAbsent(
                any(UUID.class), eq("TRIBUTE"), eq("failure:1"),
                eq("SUBSCRIPTION_CHARGE_FAILED")
        )).willReturn(1);
        givenSubscription();
        given(subscription.matchesCommercialSnapshot(
                49_900, "RUB", BillingPeriod.MONTH
        )).willReturn(true);
        given(subscription.getStatus()).willReturn(SubscriptionStatus.PAST_DUE);

        assertTrue(service.processChargeFailure(
                "TRIBUTE", "failure:1", "order-1",
                49_900, "RUB", BillingPeriod.MONTH
        ));
        verify(subscription, org.mockito.Mockito.never()).markPastDue();
    }

    private void givenNewEvent() {
        given(eventRepository.insertIfAbsent(
                any(UUID.class), eq("TRIBUTE"), eq("charge:1"),
                eq("SUBSCRIPTION_CHARGE_SUCCEEDED")
        )).willReturn(1);
    }

    private void givenSubscription() {
        given(subscriptionRepository.findByProviderAndExternalOrderIdForUpdate(
                "TRIBUTE", "order-1"
        )).willReturn(Optional.of(subscription));
    }
}
