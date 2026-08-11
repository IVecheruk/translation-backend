package com.translatelab.backend.payment.service;

import com.translatelab.backend.config.PaymentProperties;
import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchasePreparationResult;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntentStatus;
import com.translatelab.backend.payment.exception.PlanPaymentOfferNotFoundException;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionPurchaseIntentCreationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000456"
    );
    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Instant EXPIRES_AT = NOW.plus(TTL);

    @Mock
    private SubscriptionPurchaseIntentRepository intentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlanPaymentOfferRepository offerRepository;

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    @Mock
    private Clock clock;

    @Mock
    private User user;

    @Mock
    private SubscriptionPlan plan;

    @Mock
    private PlanPaymentOffer offer;

    @Mock
    private SubscriptionPurchaseIntent savedIntent;

    private SubscriptionPurchaseIntentCreationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPurchaseIntentCreationService(
                intentRepository,
                userRepository,
                offerRepository,
                subscriptionRepository,
                new PaymentProperties(TTL, "TRIBUTE"),
                clock
        );
    }

    @Test
    void shouldCreatePendingIntentInRequiredOrder() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(offerRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        "PRO",
                        "TRIBUTE",
                        BillingPeriod.MONTH
                ))
                .willReturn(Optional.of(offer));
        given(offer.isActive()).willReturn(true);
        given(offer.getPlan()).willReturn(plan);
        given(offer.getCode()).willReturn("PRO_TRIBUTE_MONTH");
        given(offer.getPriceMinor()).willReturn(49_900L);
        given(offer.getCurrency()).willReturn("RUB");
        given(offer.getBillingPeriod()).willReturn(BillingPeriod.MONTH);
        given(offer.getExternalProductId()).willReturn("tribute-product");
        given(plan.getCode()).willReturn("PRO");
        given(plan.getDisplayName()).willReturn("Профессиональный");
        given(plan.isActive()).willReturn(true);
        given(offer.getProvider()).willReturn("TRIBUTE");
        given(clock.instant()).willReturn(NOW);
        given(intentRepository.saveAndFlush(
                any(SubscriptionPurchaseIntent.class)
        )).willReturn(savedIntent);
        given(savedIntent.getId()).willReturn(INTENT_ID);
        given(savedIntent.getProvider()).willReturn("TRIBUTE");
        given(savedIntent.getExpiresAt()).willReturn(EXPIRES_AT);

        SubscriptionPurchaseIntentCreationResult result =
                service.prepare(command).intentCreationResult();

        ArgumentCaptor<SubscriptionPurchaseIntent> intentCaptor =
                ArgumentCaptor.forClass(
                        SubscriptionPurchaseIntent.class
                );
        InOrder order = inOrder(
                userRepository,
                offerRepository,
                clock,
                intentRepository
        );
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(offerRepository)
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        "PRO",
                        "TRIBUTE",
                        BillingPeriod.MONTH
                );
        order.verify(clock).instant();
        order.verify(intentRepository).saveAndFlush(
                intentCaptor.capture()
        );

        SubscriptionPurchaseIntent created = intentCaptor.getValue();
        assertAll(
                () -> assertSame(user, created.getUser()),
                () -> assertSame(plan, created.getPlan()),
                () -> assertEquals("TRIBUTE", created.getProvider()),
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        created.getStatus()
                ),
                () -> assertEquals(EXPIRES_AT, created.getExpiresAt()),
                () -> assertEquals(INTENT_ID, result.intentId()),
                () -> assertEquals("TRIBUTE", result.provider()),
                () -> assertEquals(EXPIRES_AT, result.expiresAt())
        );
    }

    @Test
    void shouldPrepareIntentAndCheckoutFromTrustedOfferData() {
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(offerRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        "PRO",
                        "TRIBUTE",
                        BillingPeriod.MONTH
                ))
                .willReturn(Optional.of(offer));
        given(offer.isActive()).willReturn(true);
        given(offer.getPlan()).willReturn(plan);
        given(offer.getCode()).willReturn("PRO_TRIBUTE_MONTH");
        given(offer.getProvider()).willReturn("TRIBUTE");
        given(offer.getPriceMinor()).willReturn(49_900L);
        given(offer.getCurrency()).willReturn("RUB");
        given(offer.getBillingPeriod()).willReturn(BillingPeriod.MONTH);
        given(offer.getExternalProductId()).willReturn("tribute-product");
        given(plan.getCode()).willReturn("PRO");
        given(plan.getDisplayName()).willReturn("Профессиональный");
        given(plan.isActive()).willReturn(true);
        given(clock.instant()).willReturn(NOW);
        given(intentRepository.saveAndFlush(
                any(SubscriptionPurchaseIntent.class)
        )).willReturn(savedIntent);
        given(savedIntent.getId()).willReturn(INTENT_ID);
        given(savedIntent.getProvider()).willReturn("TRIBUTE");
        given(savedIntent.getExpiresAt()).willReturn(EXPIRES_AT);

        SubscriptionPurchasePreparationResult result =
                service.prepare(validCommand());

        SubscriptionPurchaseIntentCreationResult intentResult =
                result.intentCreationResult();
        PaymentCheckoutCreationCommand checkoutCommand =
                result.checkoutCommand();

        assertAll(
                () -> assertEquals(INTENT_ID, intentResult.intentId()),
                () -> assertEquals("TRIBUTE", intentResult.provider()),
                () -> assertEquals(EXPIRES_AT, intentResult.expiresAt()),
                () -> assertEquals(INTENT_ID, checkoutCommand.intentId()),
                () -> assertEquals(
                        "PRO_TRIBUTE_MONTH",
                        checkoutCommand.offerCode()
                ),
                () -> assertEquals("PRO", checkoutCommand.planCode()),
                () -> assertEquals(
                        "Профессиональный",
                        checkoutCommand.planDisplayName()
                ),
                () -> assertEquals(49_900L, checkoutCommand.priceMinor()),
                () -> assertEquals("RUB", checkoutCommand.currency()),
                () -> assertEquals(
                        BillingPeriod.MONTH,
                        checkoutCommand.billingPeriod()
                ),
                () -> assertEquals(
                        "tribute-product",
                        checkoutCommand.externalProductId()
                ),
                () -> assertEquals(EXPIRES_AT, checkoutCommand.expiresAt())
        );
    }

    @Test
    void shouldPreserveMissingExternalProductIdInPreparation() {
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(offerRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        "PRO",
                        "TRIBUTE",
                        BillingPeriod.MONTH
                ))
                .willReturn(Optional.of(offer));
        given(offer.isActive()).willReturn(true);
        given(offer.getPlan()).willReturn(plan);
        given(offer.getCode()).willReturn("PRO_TRIBUTE_MONTH");
        given(offer.getProvider()).willReturn("TRIBUTE");
        given(offer.getPriceMinor()).willReturn(49_900L);
        given(offer.getCurrency()).willReturn("RUB");
        given(offer.getBillingPeriod()).willReturn(BillingPeriod.MONTH);
        given(plan.getCode()).willReturn("PRO");
        given(plan.getDisplayName()).willReturn("Профессиональный");
        given(plan.isActive()).willReturn(true);
        given(clock.instant()).willReturn(NOW);
        given(intentRepository.saveAndFlush(
                any(SubscriptionPurchaseIntent.class)
        )).willReturn(savedIntent);
        given(savedIntent.getId()).willReturn(INTENT_ID);
        given(savedIntent.getProvider()).willReturn("TRIBUTE");
        given(savedIntent.getExpiresAt()).willReturn(EXPIRES_AT);

        SubscriptionPurchasePreparationResult result =
                service.prepare(validCommand());

        assertNull(result.checkoutCommand().externalProductId());
    }

    @Test
    void shouldRejectNullPreparationCommandBeforeRepositoryCalls() {
        assertThrows(
                NullPointerException.class,
                () -> service.prepare(null)
        );

        verifyNoInteractions(
                userRepository,
                offerRepository,
                clock,
                intentRepository
        );
    }

    @Test
    void shouldRejectMissingUserBeforeLoadingPlan() {
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> service.prepare(validCommand())
        );

        verifyNoInteractions(offerRepository, clock, intentRepository);
    }

    @Test
    void shouldRejectMissingOfferBeforeReadingClock() {
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(offerRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        "PRO",
                        "TRIBUTE",
                        BillingPeriod.MONTH
                ))
                .willReturn(Optional.empty());

        assertThrows(
                PlanPaymentOfferNotFoundException.class,
                () -> service.prepare(validCommand())
        );

        verifyNoInteractions(clock, intentRepository);
    }

    @Test
    void shouldRejectFreePlanBeforeReadingClock() {
        SubscriptionPurchaseIntentCreationCommand command =
                new SubscriptionPurchaseIntentCreationCommand(
                        USER_ID,
                        "FREE",
                        "TRIBUTE"
        );
        given(userRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(user));
        given(offerRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        "FREE",
                        "TRIBUTE",
                        BillingPeriod.MONTH
                ))
                .willReturn(Optional.of(offer));
        given(offer.getPlan()).willReturn(plan);
        given(plan.getCode()).willReturn("FREE");

        assertThrows(
                PlanPaymentOfferNotFoundException.class,
                () -> service.prepare(command)
        );

        verifyNoInteractions(clock, intentRepository);
    }

    @Test
    void shouldDeclareTransactionalPreparationBoundary()
            throws NoSuchMethodException {
        Method prepareMethod = SubscriptionPurchaseIntentCreationService.class
                .getMethod(
                        "prepare",
                        SubscriptionPurchaseIntentCreationCommand.class
                );

        assertAll(
                () -> assertTrue(
                        prepareMethod.isAnnotationPresent(
                                Transactional.class
                        )
                ),
                () -> assertFalse(
                        prepareMethod.getAnnotation(
                                Transactional.class
                        ).readOnly()
                )
        );
    }

    private SubscriptionPurchaseIntentCreationCommand validCommand() {
        return new SubscriptionPurchaseIntentCreationCommand(
                USER_ID,
                "PRO",
                "TRIBUTE"
        );
    }
}
