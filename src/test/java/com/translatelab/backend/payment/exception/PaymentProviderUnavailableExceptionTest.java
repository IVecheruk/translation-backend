package com.translatelab.backend.payment.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PaymentProviderUnavailableExceptionTest {

    private static final String SAFE_MESSAGE =
            "Платёжный сервис временно недоступен";

    @Test
    void shouldExposeSafeMessageWithoutCause() {
        PaymentProviderUnavailableException exception =
                new PaymentProviderUnavailableException();

        assertEquals(SAFE_MESSAGE, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void shouldPreserveCauseWithoutExposingItsMessage() {
        RuntimeException cause = new RuntimeException(
                "https://provider.internal secret-token"
        );

        PaymentProviderUnavailableException exception =
                new PaymentProviderUnavailableException(cause);

        assertEquals(SAFE_MESSAGE, exception.getMessage());
        assertSame(cause, exception.getCause());
        assertFalse(exception.getMessage().contains(cause.getMessage()));
    }
}
