package com.translatelab.backend.plan.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanEntitlementIdTest {

    @Test
    void shouldCreateIdentifier() {
        PlanEntitlementId id = new PlanEntitlementId(
                "FREE",
                FeatureCode.DOCUMENT_TRANSLATION
        );

        assertAll(
                () -> assertEquals("FREE", id.getPlanCode()),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        id.getFeatureCode()
                )
        );
    }

    @Test
    void shouldCompareIdentifiersByBothComponents() {
        PlanEntitlementId first = new PlanEntitlementId(
                "FREE",
                FeatureCode.DOCUMENT_TRANSLATION
        );
        PlanEntitlementId equal = new PlanEntitlementId(
                "FREE",
                FeatureCode.DOCUMENT_TRANSLATION
        );
        PlanEntitlementId differentPlan = new PlanEntitlementId(
                "PRO",
                FeatureCode.DOCUMENT_TRANSLATION
        );

        assertAll(
                () -> assertEquals(first, equal),
                () -> assertEquals(
                        first.hashCode(),
                        equal.hashCode()
                ),
                () -> assertNotEquals(first, differentPlan),
                () -> assertNotEquals(first, null),
                () -> assertNotEquals(first, "FREE")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "free",
            "1FREE",
            "FREE-PLAN",
            "FREE PLAN",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567"
    })
    void shouldRejectInvalidPlanCode(String planCode) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanEntitlementId(
                        planCode,
                        FeatureCode.DOCUMENT_TRANSLATION
                )
        );
    }

    @Test
    void shouldRejectNullFeatureCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanEntitlementId(
                        "FREE",
                        null
                )
        );
    }
}
