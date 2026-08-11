package com.translatelab.backend.payment.provider.tribute.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InvalidTributeWebhookSignatureExceptionTest {

    @Test
    void shouldExposeOnlyFixedSafeMessage() {
        InvalidTributeWebhookSignatureException exception =
                new InvalidTributeWebhookSignatureException();

        assertAll(
                () -> assertEquals(
                        "Недействительная подпись webhook Tribute",
                        exception.getMessage()
                ),
                () -> assertNull(exception.getCause())
        );
    }
}
