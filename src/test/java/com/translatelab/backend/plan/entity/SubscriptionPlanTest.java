package com.translatelab.backend.plan.entity;

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

class SubscriptionPlanTest {

    @Test
    void shouldCreateActivePlanWithNormalizedDisplayName() {
        SubscriptionPlan plan = new SubscriptionPlan(
                "FREE",
                "  Бесплатный  "
        );

        assertAll(
                () -> assertEquals("FREE", plan.getCode()),
                () -> assertEquals(
                        "Бесплатный",
                        plan.getDisplayName()
                ),
                () -> assertTrue(plan.isActive()),
                () -> assertNull(plan.getCreatedAt()),
                () -> assertNull(plan.getUpdatedAt())
        );
    }

    @Test
    void shouldDeactivateAndActivatePlan() {
        SubscriptionPlan plan = createPlan();

        plan.deactivate();

        assertFalse(plan.isActive());

        plan.activate();

        assertTrue(plan.isActive());
    }

    @Test
    void shouldRenamePlanAndNormalizeDisplayName() {
        SubscriptionPlan plan = createPlan();

        plan.rename("  Базовый тариф  ");

        assertEquals(
                "Базовый тариф",
                plan.getDisplayName()
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
    void shouldRejectInvalidPlanCode(String code) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPlan(
                        code,
                        "Бесплатный"
                )
        );
    }

    @Test
    void shouldAcceptMaximumLengthPlanCode() {
        String code = "A".repeat(32);

        SubscriptionPlan plan = new SubscriptionPlan(
                code,
                "Максимальный код"
        );

        assertEquals(code, plan.getCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "   "
    })
    void shouldRejectMissingDisplayName(String displayName) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPlan(
                        "FREE",
                        displayName
                )
        );
    }

    @Test
    void shouldRejectTooLongDisplayName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPlan(
                        "FREE",
                        "a".repeat(101)
                )
        );
    }

    @Test
    void shouldPreserveDisplayNameWhenRenameFails() {
        SubscriptionPlan plan = createPlan();

        assertThrows(
                IllegalArgumentException.class,
                () -> plan.rename("a".repeat(101))
        );

        assertEquals(
                "Бесплатный",
                plan.getDisplayName()
        );
    }

    private SubscriptionPlan createPlan() {
        return new SubscriptionPlan(
                "FREE",
                "Бесплатный"
        );
    }
}
