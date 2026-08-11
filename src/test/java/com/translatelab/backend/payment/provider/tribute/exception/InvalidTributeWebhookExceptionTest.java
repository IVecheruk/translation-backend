package com.translatelab.backend.payment.provider.tribute.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class InvalidTributeWebhookExceptionTest {

    private static final String SAFE_MESSAGE =
            "Некорректные данные webhook Tribute";

    @Test
    void shouldExposeOnlyFixedSafeMessage() {
        InvalidTributeWebhookException exception =
                new InvalidTributeWebhookException();

        assertAll(
                () -> assertEquals(
                        SAFE_MESSAGE,
                        exception.getMessage()
                ),
                () -> assertNull(exception.getCause())
        );
    }

    @Test
    void shouldRetainCauseWithoutIncludingItsDetailsInMessage() {
        IllegalArgumentException cause = new IllegalArgumentException(
                "payload contains private@example.test "
                        + "and synthetic-payment-token"
        );

        InvalidTributeWebhookException exception =
                new InvalidTributeWebhookException(cause);

        assertAll(
                () -> assertEquals(
                        SAFE_MESSAGE,
                        exception.getMessage()
                ),
                () -> assertSame(cause, exception.getCause())
        );
    }
}
