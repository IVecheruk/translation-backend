package com.translatelab.backend.plan.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanEntitlementTest {

    @Test
    void shouldCreateLimitedEntitlement() {
        SubscriptionPlan plan = createPlan();

        PlanEntitlement entitlement = PlanEntitlement.limited(
                plan,
                FeatureCode.DOCUMENT_TRANSLATION,
                5,
                PeriodType.MONTH
        );

        assertAll(
                () -> assertEquals(
                        new PlanEntitlementId(
                                "FREE",
                                FeatureCode.DOCUMENT_TRANSLATION
                        ),
                        entitlement.getId()
                ),
                () -> assertSame(plan, entitlement.getPlan()),
                () -> assertEquals(5, entitlement.getLimitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        entitlement.getPeriodType()
                ),
                () -> assertFalse(entitlement.isUnlimited()),
                () -> assertNull(entitlement.getCreatedAt()),
                () -> assertNull(entitlement.getUpdatedAt())
        );
    }

    @Test
    void shouldCreateUnlimitedEntitlement() {
        SubscriptionPlan plan = createPlan();

        PlanEntitlement entitlement = PlanEntitlement.unlimited(
                plan,
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH
        );

        assertAll(
                () -> assertEquals(
                        new PlanEntitlementId(
                                "FREE",
                                FeatureCode.DOCUMENT_TRANSLATION
                        ),
                        entitlement.getId()
                ),
                () -> assertSame(plan, entitlement.getPlan()),
                () -> assertNull(entitlement.getLimitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        entitlement.getPeriodType()
                ),
                () -> assertTrue(entitlement.isUnlimited())
        );
    }

    @Test
    void shouldMakeLimitedEntitlementUnlimited() {
        PlanEntitlement entitlement = PlanEntitlement.limited(
                createPlan(),
                FeatureCode.DOCUMENT_TRANSLATION,
                5,
                PeriodType.MONTH
        );

        entitlement.makeUnlimited();

        assertAll(
                () -> assertTrue(entitlement.isUnlimited()),
                () -> assertNull(entitlement.getLimitUnits())
        );
    }

    @Test
    void shouldMakeUnlimitedEntitlementLimited() {
        PlanEntitlement entitlement = PlanEntitlement.unlimited(
                createPlan(),
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH
        );

        entitlement.makeLimited(10);

        assertAll(
                () -> assertFalse(entitlement.isUnlimited()),
                () -> assertEquals(10, entitlement.getLimitUnits())
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldRejectInvalidLimitWhenCreatingLimitedEntitlement(int limitUnits) {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlanEntitlement.limited(
                        createPlan(),
                        FeatureCode.DOCUMENT_TRANSLATION,
                        limitUnits,
                        PeriodType.MONTH
                )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldPreserveUnlimitedStateWhenNewLimitIsInvalid(int limitUnits) {
        PlanEntitlement entitlement = PlanEntitlement.unlimited(
                createPlan(),
                FeatureCode.DOCUMENT_TRANSLATION,
                PeriodType.MONTH
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> entitlement.makeLimited(limitUnits)
        );

        assertAll(
                () -> assertTrue(entitlement.isUnlimited()),
                () -> assertNull(entitlement.getLimitUnits())
        );
    }

    @Test
    void shouldRejectNullPlan() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanEntitlement.limited(
                                null,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                5,
                                PeriodType.MONTH
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanEntitlement.unlimited(
                                null,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                PeriodType.MONTH
                        )
                )
        );
    }

    @Test
    void shouldRejectNullFeatureCode() {
        SubscriptionPlan plan = createPlan();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanEntitlement.limited(
                                plan,
                                null,
                                5,
                                PeriodType.MONTH
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanEntitlement.unlimited(
                                plan,
                                null,
                                PeriodType.MONTH
                        )
                )
        );
    }

    @Test
    void shouldRejectNullPeriodType() {
        SubscriptionPlan plan = createPlan();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanEntitlement.limited(
                                plan,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                5,
                                null
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> PlanEntitlement.unlimited(
                                plan,
                                FeatureCode.DOCUMENT_TRANSLATION,
                                null
                        )
                )
        );
    }

    private SubscriptionPlan createPlan() {
        return new SubscriptionPlan(
                "FREE",
                "Бесплатный"
        );
    }
}
