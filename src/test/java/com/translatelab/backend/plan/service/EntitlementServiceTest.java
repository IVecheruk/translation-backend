package com.translatelab.backend.plan.service;

import com.translatelab.backend.plan.dto.ResolvedEntitlement;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.plan.entity.PlanEntitlement;
import com.translatelab.backend.plan.entity.PlanEntitlementId;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.exception.FeatureNotAvailableException;
import com.translatelab.backend.plan.repository.PlanEntitlementRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "584175c1-d670-4ef5-91e4-896ffb80b9cc"
    );
    private static final Instant NOW = Instant.parse(
            "2026-08-01T12:00:00Z"
    );
    private static final Clock CLOCK = Clock.fixed(
            NOW,
            ZoneOffset.UTC
    );
    private static final PlanEntitlementId ENTITLEMENT_ID =
            new PlanEntitlementId(
                    "FREE",
                    FeatureCode.DOCUMENT_TRANSLATION
            );

    @Mock
    private PlanEntitlementRepository planEntitlementRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private EntitlementService service;

    @BeforeEach
    void setUp() {
        service = new EntitlementService(
                planEntitlementRepository,
                userSubscriptionRepository,
                CLOCK
        );
    }

    @Test
    void shouldResolveLimitedFreeEntitlement() {
        givenNoActiveSubscription();
        PlanEntitlement entitlement = PlanEntitlement.limited(
                createPlan(),
                FeatureCode.DOCUMENT_TRANSLATION,
                5,
                PeriodType.MONTH
        );
        given(planEntitlementRepository.findByIdAndPlan_ActiveTrue(
                ENTITLEMENT_ID
        )).willReturn(Optional.of(entitlement));

        ResolvedEntitlement resolved = service.resolveEntitlement(
                USER_ID,
                FeatureCode.DOCUMENT_TRANSLATION
        );

        assertAll(
                () -> assertEquals("FREE", resolved.planCode()),
                () -> assertEquals(
                        "Бесплатный",
                        resolved.planDisplayName()
                ),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        resolved.featureCode()
                ),
                () -> assertEquals(5, resolved.limitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        resolved.periodType()
                ),
                () -> assertFalse(resolved.unlimited())
        );
        verify(planEntitlementRepository)
                .findByIdAndPlan_ActiveTrue(ENTITLEMENT_ID);
        verify(userSubscriptionRepository)
                .findEffectiveActiveByUserIdAt(USER_ID, NOW);
    }

    @Test
    void shouldResolveUnlimitedFreeEntitlement() {
        givenNoActiveSubscription();
        PlanEntitlement entitlement = PlanEntitlement.unlimited(
                createPlan(),
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH
        );
        given(planEntitlementRepository.findByIdAndPlan_ActiveTrue(
                ENTITLEMENT_ID
        )).willReturn(Optional.of(entitlement));

        ResolvedEntitlement resolved = service.resolveEntitlement(
                USER_ID,
                FeatureCode.DOCUMENT_TRANSLATION
        );

        assertAll(
                () -> assertNull(resolved.limitUnits()),
                () -> assertTrue(resolved.unlimited())
        );
        verify(planEntitlementRepository)
                .findByIdAndPlan_ActiveTrue(ENTITLEMENT_ID);
        verify(userSubscriptionRepository)
                .findEffectiveActiveByUserIdAt(USER_ID, NOW);
    }

    @Test
    void shouldResolveEntitlementFromActivePaidSubscription() {
        SubscriptionPlan paidPlan = new SubscriptionPlan(
                "PRO",
                "Профессиональный"
        );
        UserSubscription subscription = UserSubscription.manual(
                new User("user@example.com", "password-hash"),
                paidPlan,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60)
        );
        PlanEntitlementId paidEntitlementId = new PlanEntitlementId(
                "PRO",
                FeatureCode.DOCUMENT_TRANSLATION
        );
        PlanEntitlement entitlement = PlanEntitlement.unlimited(
                paidPlan,
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH
        );
        given(userSubscriptionRepository.findEffectiveActiveByUserIdAt(
                USER_ID,
                NOW
        )).willReturn(Optional.of(subscription));
        given(planEntitlementRepository.findByIdAndPlan_ActiveTrue(
                paidEntitlementId
        )).willReturn(Optional.of(entitlement));

        ResolvedEntitlement resolved = service.resolveEntitlement(
                USER_ID,
                FeatureCode.DOCUMENT_TRANSLATION
        );

        assertAll(
                () -> assertEquals("PRO", resolved.planCode()),
                () -> assertEquals(
                        "Профессиональный",
                        resolved.planDisplayName()
                ),
                () -> assertNull(resolved.limitUnits()),
                () -> assertTrue(resolved.unlimited())
        );
        verify(userSubscriptionRepository)
                .findEffectiveActiveByUserIdAt(USER_ID, NOW);
        verify(planEntitlementRepository)
                .findByIdAndPlan_ActiveTrue(paidEntitlementId);
    }

    @Test
    void shouldUseCallerReferenceTimeAtSubscriptionBoundary() {
        Instant periodEnd = Instant.parse("2026-09-01T00:00:00Z");
        PlanEntitlement entitlement = PlanEntitlement.limited(
                createPlan(),
                FeatureCode.DOCUMENT_TRANSLATION,
                5,
                PeriodType.MONTH
        );
        given(userSubscriptionRepository.findEffectiveActiveByUserIdAt(
                USER_ID,
                periodEnd
        )).willReturn(Optional.empty());
        given(planEntitlementRepository.findByIdAndPlan_ActiveTrue(
                ENTITLEMENT_ID
        )).willReturn(Optional.of(entitlement));

        ResolvedEntitlement resolved = service.resolveEntitlement(
                USER_ID,
                FeatureCode.DOCUMENT_TRANSLATION,
                periodEnd
        );

        assertEquals("FREE", resolved.planCode());
        verify(userSubscriptionRepository)
                .findEffectiveActiveByUserIdAt(USER_ID, periodEnd);
    }

    @Test
    void shouldRejectFeatureUnavailableInActivePlan() {
        givenNoActiveSubscription();
        given(planEntitlementRepository.findByIdAndPlan_ActiveTrue(
                ENTITLEMENT_ID
        )).willReturn(Optional.empty());

        assertThrows(
                FeatureNotAvailableException.class,
                () -> service.resolveEntitlement(
                        USER_ID,
                        FeatureCode.DOCUMENT_TRANSLATION
                )
        );

        verify(planEntitlementRepository)
                .findByIdAndPlan_ActiveTrue(ENTITLEMENT_ID);
        verify(userSubscriptionRepository)
                .findEffectiveActiveByUserIdAt(USER_ID, NOW);
    }

    @Test
    void shouldRejectNullUserIdBeforeCallingRepository() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.resolveEntitlement(
                        null,
                        FeatureCode.DOCUMENT_TRANSLATION
                )
        );

        assertEquals(
                "Идентификатор пользователя не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(
                planEntitlementRepository,
                userSubscriptionRepository
        );
    }

    @Test
    void shouldRejectNullFeatureCodeBeforeCallingRepository() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.resolveEntitlement(USER_ID, null)
        );

        assertEquals(
                "Код функции не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(
                planEntitlementRepository,
                userSubscriptionRepository
        );
    }

    @Test
    void shouldRejectNullReferenceTimeBeforeCallingRepository() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.resolveEntitlement(
                        USER_ID,
                        FeatureCode.DOCUMENT_TRANSLATION,
                        null
                )
        );

        assertEquals(
                "Момент определения тарифа не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(
                planEntitlementRepository,
                userSubscriptionRepository
        );
    }

    private void givenNoActiveSubscription() {
        given(userSubscriptionRepository.findEffectiveActiveByUserIdAt(
                USER_ID,
                NOW
        )).willReturn(Optional.empty());
    }

    private SubscriptionPlan createPlan() {
        return new SubscriptionPlan("FREE", "Бесплатный");
    }
}
