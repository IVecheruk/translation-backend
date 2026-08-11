package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.subscription.entity.SubscriptionStatus;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class SubscriptionPurchaseCompletionServiceTest {

    private static final String PROVIDER = "TRIBUTE";
    private static final String EXTERNAL_EVENT_ID = "event-123";
    private static final String EXTERNAL_CHECKOUT_ID = "checkout-456";
    private static final String EXTERNAL_CUSTOMER_ID = "customer-789";
    private static final String EXTERNAL_SUBSCRIPTION_ID =
            "subscription-012";
    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Instant PERIOD_START = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Instant PERIOD_END = Instant.parse(
            "2026-10-01T00:00:00Z"
    );

    @Mock
    private ProcessedPaymentEventRepository paymentEventRepository;

    @Mock
    private SubscriptionPurchaseIntentRepository intentRepository;

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionPurchaseIntent intent;

    @Mock
    private Clock clock;

    private User user;
    private SubscriptionPlan plan;
    private SubscriptionPurchaseCompletionService service;

    @BeforeEach
    void setUp() {
        user = new User("user@example.com", "password-hash");
        plan = new SubscriptionPlan("PRO", "Профессиональный");
        service = new SubscriptionPurchaseCompletionService(
                paymentEventRepository,
                intentRepository,
                subscriptionRepository,
                userRepository,
                clock
        );
    }

    @Test
    void shouldCompleteNewPurchaseInRequiredOrder() {
        givenNewEvent();
        givenIntent();
        given(clock.instant()).willReturn(NOW);
        ArgumentCaptor<UserSubscription> subscriptionCaptor =
                ArgumentCaptor.forClass(UserSubscription.class);

        boolean processed = service.processCompletion(validCommand());

        InOrder order = inOrder(
                paymentEventRepository,
                intentRepository,
                userRepository,
                clock,
                intent,
                subscriptionRepository
        );
        order.verify(paymentEventRepository).insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_PURCHASE_COMPLETED")
        );
        order.verify(intentRepository)
                .findByProviderAndExternalCheckoutId(
                        PROVIDER,
                        EXTERNAL_CHECKOUT_ID
                );
        order.verify(userRepository).findByIdForUpdate(null);
        order.verify(intentRepository)
                .findByProviderAndExternalCheckoutIdForUpdate(
                        PROVIDER,
                        EXTERNAL_CHECKOUT_ID
                );
        order.verify(clock).instant();
        order.verify(subscriptionRepository).findLiveByUserIdForUpdate(null);
        order.verify(intent).consume(NOW);
        order.verify(subscriptionRepository).saveAndFlush(
                subscriptionCaptor.capture()
        );

        UserSubscription saved = subscriptionCaptor.getValue();
        assertAll(
                () -> assertTrue(processed),
                () -> assertSame(user, saved.getUser()),
                () -> assertSame(plan, saved.getPlan()),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        saved.getStatus()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        saved.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        saved.getCurrentPeriodEnd()
                ),
                () -> assertEquals(PROVIDER, saved.getProvider()),
                () -> assertEquals(
                        EXTERNAL_CUSTOMER_ID,
                        saved.getExternalCustomerId()
                ),
                () -> assertEquals(
                        EXTERNAL_SUBSCRIPTION_ID,
                        saved.getExternalOrderId()
                ),
                () -> assertEquals(
                        null,
                        saved.getExternalSubscriptionId()
                )
        );
    }

    @Test
    void shouldReturnFalseWithoutLoadingIntentForDuplicateEvent() {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_PURCHASE_COMPLETED")
        )).willReturn(0);

        boolean processed = service.processCompletion(validCommand());

        assertFalse(processed);
        verifyNoInteractions(
                intentRepository,
                subscriptionRepository,
                userRepository,
                intent,
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
                eq("SUBSCRIPTION_PURCHASE_COMPLETED")
        )).willReturn(insertedRows);

        assertThrows(
                IllegalStateException.class,
                () -> service.processCompletion(validCommand())
        );

        verifyNoInteractions(
                intentRepository,
                subscriptionRepository,
                userRepository,
                intent,
                clock
        );
    }

    @Test
    void shouldThrowWithoutReadingClockWhenIntentDoesNotExist() {
        givenNewEvent();
        given(intentRepository
                .findByProviderAndExternalCheckoutId(
                        PROVIDER,
                        EXTERNAL_CHECKOUT_ID
                )).willReturn(Optional.empty());

        assertThrows(
                SubscriptionPurchaseIntentNotFoundException.class,
                () -> service.processCompletion(validCommand())
        );

        verifyNoInteractions(subscriptionRepository, userRepository, intent, clock);
    }

    @Test
    void shouldPropagateIntentConsumptionFailureWithoutSavingSubscription() {
        IllegalStateException domainFailure = new IllegalStateException(
                "Заявка просрочена"
        );
        givenNewEvent();
        givenIntent();
        given(clock.instant()).willReturn(NOW);
        willThrow(domainFailure).given(intent).consume(NOW);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.processCompletion(validCommand())
        );

        assertSame(domainFailure, thrown);
        org.mockito.Mockito.verify(subscriptionRepository, org.mockito.Mockito.never())
                .saveAndFlush(any());
    }

    @Test
    void shouldRejectNullCommandBeforeRepositoryCalls() {
        assertThrows(
                NullPointerException.class,
                () -> service.processCompletion(null)
        );

        verifyNoInteractions(
                paymentEventRepository,
                intentRepository,
                subscriptionRepository,
                userRepository,
                intent,
                clock
        );
    }

    private void givenNewEvent() {
        given(paymentEventRepository.insertIfAbsent(
                any(UUID.class),
                eq(PROVIDER),
                eq(EXTERNAL_EVENT_ID),
                eq("SUBSCRIPTION_PURCHASE_COMPLETED")
        )).willReturn(1);
    }

    private void givenIntent() {
        given(intentRepository
                .findByProviderAndExternalCheckoutId(
                        PROVIDER,
                        EXTERNAL_CHECKOUT_ID
                )).willReturn(Optional.of(intent));
        given(intentRepository
                .findByProviderAndExternalCheckoutIdForUpdate(
                        PROVIDER,
                        EXTERNAL_CHECKOUT_ID
                )).willReturn(Optional.of(intent));
        given(intent.getUser()).willReturn(user);
        given(userRepository.findByIdForUpdate(null))
                .willReturn(Optional.of(user));
        given(intent.getPlan()).willReturn(plan);
        given(intent.matchesCommercialSnapshot(
                99_900L,
                "RUB",
                BillingPeriod.MONTH,
                null
        )).willReturn(true);
    }

    private SubscriptionPurchaseCompletionCommand validCommand() {
        return new SubscriptionPurchaseCompletionCommand(
                PROVIDER,
                EXTERNAL_EVENT_ID,
                EXTERNAL_CHECKOUT_ID,
                EXTERNAL_SUBSCRIPTION_ID,
                EXTERNAL_CUSTOMER_ID,
                null,
                99_900L,
                "RUB",
                BillingPeriod.MONTH,
                null,
                PERIOD_START,
                PERIOD_END
        );
    }
}
