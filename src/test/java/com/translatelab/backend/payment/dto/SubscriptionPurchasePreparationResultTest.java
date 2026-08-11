package com.translatelab.backend.payment.dto;

import com.translatelab.backend.payment.entity.BillingPeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionPurchasePreparationResultTest {

    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final Instant EXPIRES_AT = Instant.parse(
            "2026-09-01T00:30:00Z"
    );

    @Test
    void shouldPreserveMatchingPreparationParts() {
        SubscriptionPurchaseIntentCreationResult intentResult =
                intentResult(INTENT_ID, EXPIRES_AT);
        PaymentCheckoutCreationCommand checkoutCommand =
                checkoutCommand(INTENT_ID, EXPIRES_AT);

        SubscriptionPurchasePreparationResult preparation =
                new SubscriptionPurchasePreparationResult(
                        intentResult,
                        checkoutCommand
                );

        assertSame(
                intentResult,
                preparation.intentCreationResult()
        );
        assertSame(checkoutCommand, preparation.checkoutCommand());
    }

    @Test
    void shouldRejectNullIntentCreationResult() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchasePreparationResult(
                        null,
                        checkoutCommand(INTENT_ID, EXPIRES_AT)
                )
        );

        assertEquals(
                "Результат создания заявки не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullCheckoutCommand() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchasePreparationResult(
                        intentResult(INTENT_ID, EXPIRES_AT),
                        null
                )
        );

        assertEquals(
                "Команда создания checkout не должна быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMismatchedIntentIds() {
        UUID otherIntentId = UUID.fromString(
                "00000000-0000-0000-0000-000000000456"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchasePreparationResult(
                        intentResult(INTENT_ID, EXPIRES_AT),
                        checkoutCommand(otherIntentId, EXPIRES_AT)
                )
        );

        assertEquals(
                "Идентификаторы подготовленной заявки должны совпадать",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMismatchedExpirations() {
        Instant otherExpiration = EXPIRES_AT.plusSeconds(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchasePreparationResult(
                        intentResult(INTENT_ID, EXPIRES_AT),
                        checkoutCommand(INTENT_ID, otherExpiration)
                )
        );

        assertEquals(
                "Сроки действия подготовленной заявки должны совпадать",
                exception.getMessage()
        );
    }

    private SubscriptionPurchaseIntentCreationResult intentResult(
            UUID intentId,
            Instant expiresAt
    ) {
        return new SubscriptionPurchaseIntentCreationResult(
                intentId,
                "TRIBUTE",
                expiresAt
        );
    }

    private PaymentCheckoutCreationCommand checkoutCommand(
            UUID intentId,
            Instant expiresAt
    ) {
        return new PaymentCheckoutCreationCommand(
                intentId,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Профессиональный",
                49900,
                "RUB",
                BillingPeriod.MONTH,
                null,
                expiresAt
        );
    }
}
