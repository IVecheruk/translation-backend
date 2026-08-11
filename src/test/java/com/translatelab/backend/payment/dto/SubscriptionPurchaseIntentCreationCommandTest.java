package com.translatelab.backend.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionPurchaseIntentCreationCommandTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );

    @Test
    void shouldPreserveValidValues() {
        SubscriptionPurchaseIntentCreationCommand command =
                new SubscriptionPurchaseIntentCreationCommand(
                        USER_ID,
                        "PRO_MONTHLY",
                        "TRIBUTE"
                );

        assertAll(
                () -> assertEquals(USER_ID, command.userId()),
                () -> assertEquals("PRO_MONTHLY", command.planCode()),
                () -> assertEquals("TRIBUTE", command.provider())
        );
    }

    @Test
    void shouldRejectNullUserId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCreationCommand(
                        null,
                        "PRO",
                        "TRIBUTE"
                )
        );

        assertEquals(
                "Идентификатор пользователя не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "PRO_MONTHLY_1",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidPlanCodeBoundaries(String planCode) {
        SubscriptionPurchaseIntentCreationCommand command =
                new SubscriptionPurchaseIntentCreationCommand(
                        USER_ID,
                        planCode,
                        "TRIBUTE"
                );

        assertEquals(planCode, command.planCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "pro",
            "1PRO",
            "PRO-MONTHLY",
            "PRO MONTHLY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidPlanCode(String planCode) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCreationCommand(
                        USER_ID,
                        planCode,
                        "TRIBUTE"
                )
        );

        assertEquals(
                "Некорректный формат кода тарифа",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "TRIBUTE_1",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidProviderBoundaries(String provider) {
        SubscriptionPurchaseIntentCreationCommand command =
                new SubscriptionPurchaseIntentCreationCommand(
                        USER_ID,
                        "PRO",
                        provider
                );

        assertEquals(provider, command.provider());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "TRIBUTE-PAY",
            "TRIBUTE PAY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidProvider(String provider) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCreationCommand(
                        USER_ID,
                        "PRO",
                        provider
                )
        );

        assertEquals(
                "Некорректный формат кода платёжного провайдера",
                exception.getMessage()
        );
    }
}
