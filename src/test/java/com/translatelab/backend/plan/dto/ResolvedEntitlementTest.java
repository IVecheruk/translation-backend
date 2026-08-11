package com.translatelab.backend.plan.dto;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedEntitlementTest {

    @Test
    void shouldCreateLimitedEntitlement() {
        ResolvedEntitlement entitlement = new ResolvedEntitlement(
                "FREE",
                "Бесплатный",
                FeatureCode.DOCUMENT_TRANSLATION,
                5,
                PeriodType.MONTH,
                false
        );

        assertAll(
                () -> assertEquals("FREE", entitlement.planCode()),
                () -> assertEquals(
                        "Бесплатный",
                        entitlement.planDisplayName()
                ),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        entitlement.featureCode()
                ),
                () -> assertEquals(5, entitlement.limitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        entitlement.periodType()
                ),
                () -> assertFalse(entitlement.unlimited())
        );
    }

    @Test
    void shouldCreateUnlimitedEntitlement() {
        ResolvedEntitlement entitlement = new ResolvedEntitlement(
                "PRO",
                "Профессиональный",
                FeatureCode.DOCUMENT_TRANSLATION,
                null,
                PeriodType.MONTH,
                true
        );

        assertAll(
                () -> assertEquals("PRO", entitlement.planCode()),
                () -> assertNull(entitlement.limitUnits()),
                () -> assertTrue(entitlement.unlimited())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectMissingPlanCode(String planCode) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedEntitlement(
                        planCode,
                        "Бесплатный",
                        FeatureCode.DOCUMENT_TRANSLATION,
                        5,
                        PeriodType.MONTH,
                        false
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectMissingPlanDisplayName(String planDisplayName) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedEntitlement(
                        "FREE",
                        planDisplayName,
                        FeatureCode.DOCUMENT_TRANSLATION,
                        5,
                        PeriodType.MONTH,
                        false
                )
        );
    }

    @Test
    void shouldRejectMissingFeatureCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedEntitlement(
                        "FREE",
                        "Бесплатный",
                        null,
                        5,
                        PeriodType.MONTH,
                        false
                )
        );
    }

    @Test
    void shouldRejectMissingPeriodType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedEntitlement(
                        "FREE",
                        "Бесплатный",
                        FeatureCode.DOCUMENT_TRANSLATION,
                        5,
                        null,
                        false
                )
        );
    }

    @Test
    void shouldRejectLimitForUnlimitedEntitlement() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedEntitlement(
                        "PRO",
                        "Профессиональный",
                        FeatureCode.DOCUMENT_TRANSLATION,
                        100,
                        PeriodType.MONTH,
                        true
                )
        );
    }

    @Test
    void shouldRejectMissingLimitForLimitedEntitlement() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedEntitlement(
                        "FREE",
                        "Бесплатный",
                        FeatureCode.DOCUMENT_TRANSLATION,
                        null,
                        PeriodType.MONTH,
                        false
                )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void shouldRejectNonPositiveLimitForLimitedEntitlement(
            int limitUnits
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedEntitlement(
                        "FREE",
                        "Бесплатный",
                        FeatureCode.DOCUMENT_TRANSLATION,
                        limitUnits,
                        PeriodType.MONTH,
                        false
                )
        );
    }
}
