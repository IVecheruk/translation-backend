package com.translatelab.backend.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionPurchaseIntentCheckoutAttachmentCommandTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000456"
    );

    @Test
    void shouldPreserveIdsAndNormalizeCheckoutId() {
        SubscriptionPurchaseIntentCheckoutAttachmentCommand command =
                new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                        USER_ID,
                        INTENT_ID,
                        "  checkout-123  "
                );

        assertAll(
                () -> assertEquals(USER_ID, command.userId()),
                () -> assertEquals(INTENT_ID, command.intentId()),
                () -> assertEquals(
                        "checkout-123",
                        command.externalCheckoutId()
                )
        );
    }

    @Test
    void shouldRejectNullUserId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                        null,
                        INTENT_ID,
                        "checkout-123"
                )
        );

        assertEquals(
                "Идентификатор пользователя не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullIntentId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                        USER_ID,
                        null,
                        "checkout-123"
                )
        );

        assertEquals(
                "Идентификатор заявки не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullCheckoutId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                        USER_ID,
                        INTENT_ID,
                        null
                )
        );

        assertEquals(
                "Внешний идентификатор checkout не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectBlankCheckoutId(String checkoutId) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                        USER_ID,
                        INTENT_ID,
                        checkoutId
                )
        );

        assertEquals(
                "Внешний идентификатор checkout не должен быть пустым",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptNormalizedCheckoutIdAtMaximumLength() {
        String checkoutId = "x".repeat(255);

        SubscriptionPurchaseIntentCheckoutAttachmentCommand command =
                new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                        USER_ID,
                        INTENT_ID,
                        "  " + checkoutId + "  "
                );

        assertEquals(checkoutId, command.externalCheckoutId());
    }

    @Test
    void shouldRejectCheckoutIdLongerThanMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                        USER_ID,
                        INTENT_ID,
                        "x".repeat(256)
                )
        );

        assertEquals(
                "Внешний идентификатор checkout "
                        + "не должен превышать 255 символов",
                exception.getMessage()
        );
    }
}
