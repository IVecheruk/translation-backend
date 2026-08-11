package com.translatelab.backend.payment.dto;

import com.translatelab.backend.payment.entity.BillingPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCheckoutCreationCommandTest {

    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final Instant EXPIRES_AT = Instant.parse(
            "2026-09-01T00:30:00Z"
    );

    @Test
    void shouldNormalizeAndPreserveValidValues() {
        PaymentCheckoutCreationCommand command = command(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "  Профессиональный  ",
                49900,
                "RUB",
                BillingPeriod.MONTH,
                "  tribute-product-123  ",
                EXPIRES_AT
        );

        assertAll(
                () -> assertEquals(INTENT_ID, command.intentId()),
                () -> assertEquals(
                        "PRO_TRIBUTE_MONTH",
                        command.offerCode()
                ),
                () -> assertEquals("PRO", command.planCode()),
                () -> assertEquals(
                        "Профессиональный",
                        command.planDisplayName()
                ),
                () -> assertEquals(49900L, command.priceMinor()),
                () -> assertEquals("RUB", command.currency()),
                () -> assertEquals(
                        BillingPeriod.MONTH,
                        command.billingPeriod()
                ),
                () -> assertEquals(
                        "tribute-product-123",
                        command.externalProductId()
                ),
                () -> assertEquals(EXPIRES_AT, command.expiresAt())
        );
    }

    @Test
    void shouldAcceptMissingExternalProductId() {
        PaymentCheckoutCreationCommand command = validCommand(null);

        assertNull(command.externalProductId());
    }

    @Test
    void shouldRejectNullIntentId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> command(
                        null,
                        "PRO_TRIBUTE_MONTH",
                        "PRO",
                        "Профессиональный",
                        49900,
                        "RUB",
                        BillingPeriod.MONTH,
                        null,
                        EXPIRES_AT
                )
        );

        assertEquals(
                "Идентификатор заявки не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "pro",
            "1PRO",
            "PRO-TRIBUTE",
            "PRO TRIBUTE"
    })
    void shouldRejectInvalidOfferCode(String offerCode) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithCodes(offerCode, "PRO")
        );

        assertEquals(
                "Некорректный формат кода платёжного предложения",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptOfferCodeAtMaximumLength() {
        String offerCode = "A" + "1".repeat(63);

        PaymentCheckoutCreationCommand command = commandWithCodes(
                offerCode,
                "PRO"
        );

        assertEquals(offerCode, command.offerCode());
    }

    @Test
    void shouldRejectOfferCodeAboveMaximumLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithCodes(
                        "A" + "1".repeat(64),
                        "PRO"
                )
        );

        assertEquals(
                "Некорректный формат кода платёжного предложения",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "pro",
            "1PRO",
            "PRO-PLUS",
            "PRO PLUS"
    })
    void shouldRejectInvalidPlanCode(String planCode) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithCodes(
                        "PRO_TRIBUTE_MONTH",
                        planCode
                )
        );

        assertEquals(
                "Некорректный формат кода тарифа",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptPlanCodeAtMaximumLength() {
        String planCode = "A" + "1".repeat(31);

        PaymentCheckoutCreationCommand command = commandWithCodes(
                "PRO_TRIBUTE_MONTH",
                planCode
        );

        assertEquals(planCode, command.planCode());
    }

    @Test
    void shouldRejectPlanCodeAboveMaximumLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithCodes(
                        "PRO_TRIBUTE_MONTH",
                        "A" + "1".repeat(32)
                )
        );

        assertEquals(
                "Некорректный формат кода тарифа",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullPlanDisplayName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithPlanDisplayName(null)
        );

        assertEquals(
                "Название тарифа не должно быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void shouldRejectEmptyPlanDisplayName(String planDisplayName) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithPlanDisplayName(planDisplayName)
        );

        assertEquals(
                "Название тарифа не должно быть пустым",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptPlanDisplayNameAtMaximumLength() {
        String planDisplayName = "я".repeat(100);

        PaymentCheckoutCreationCommand command =
                commandWithPlanDisplayName(planDisplayName);

        assertEquals(planDisplayName, command.planDisplayName());
    }

    @Test
    void shouldRejectPlanDisplayNameAboveMaximumLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithPlanDisplayName("я".repeat(101))
        );

        assertEquals(
                "Название тарифа не должно превышать 100 символов",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void shouldRejectNonPositivePrice(long priceMinor) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithPrice(priceMinor)
        );

        assertEquals(
                "Цена должна быть больше нуля",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void shouldAcceptPositivePriceBoundaries(long priceMinor) {
        PaymentCheckoutCreationCommand command = commandWithPrice(
                priceMinor
        );

        assertEquals(priceMinor, command.priceMinor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "rub",
            "RuB",
            "RU",
            "RUBB",
            "R1B"
    })
    void shouldRejectInvalidCurrency(String currency) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandWithCurrency(currency)
        );

        assertEquals(
                "Валюта должна состоять из трёх заглавных латинских букв",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUB", "USD", "EUR"})
    void shouldAcceptValidCurrency(String currency) {
        PaymentCheckoutCreationCommand command = commandWithCurrency(
                currency
        );

        assertEquals(currency, command.currency());
    }

    @Test
    void shouldRejectNullBillingPeriod() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> command(
                        INTENT_ID,
                        "PRO_TRIBUTE_MONTH",
                        "PRO",
                        "Профессиональный",
                        49900,
                        "RUB",
                        null,
                        null,
                        EXPIRES_AT
                )
        );

        assertEquals(
                "Период оплаты не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void shouldRejectEmptyExternalProductId(String externalProductId) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validCommand(externalProductId)
        );

        assertEquals(
                "Внешний идентификатор продукта не должен быть пустым",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptExternalProductIdAtMaximumLength() {
        String externalProductId = "a".repeat(255);

        PaymentCheckoutCreationCommand command = validCommand(
                externalProductId
        );

        assertEquals(
                externalProductId,
                command.externalProductId()
        );
    }

    @Test
    void shouldRejectExternalProductIdAboveMaximumLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validCommand("a".repeat(256))
        );

        assertEquals(
                "Внешний идентификатор продукта "
                        + "не должен превышать 255 символов",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullExpiration() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> command(
                        INTENT_ID,
                        "PRO_TRIBUTE_MONTH",
                        "PRO",
                        "Профессиональный",
                        49900,
                        "RUB",
                        BillingPeriod.MONTH,
                        null,
                        null
                )
        );

        assertEquals(
                "Срок действия заявки не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldPreserveExpirationWithoutReadingCurrentTime() {
        Instant historicalExpiration = Instant.EPOCH;

        PaymentCheckoutCreationCommand command = command(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Профессиональный",
                49900,
                "RUB",
                BillingPeriod.MONTH,
                null,
                historicalExpiration
        );

        assertEquals(historicalExpiration, command.expiresAt());
    }

    private PaymentCheckoutCreationCommand validCommand(
            String externalProductId
    ) {
        return command(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Профессиональный",
                49900,
                "RUB",
                BillingPeriod.MONTH,
                externalProductId,
                EXPIRES_AT
        );
    }

    private PaymentCheckoutCreationCommand commandWithCodes(
            String offerCode,
            String planCode
    ) {
        return command(
                INTENT_ID,
                offerCode,
                planCode,
                "Профессиональный",
                49900,
                "RUB",
                BillingPeriod.MONTH,
                null,
                EXPIRES_AT
        );
    }

    private PaymentCheckoutCreationCommand commandWithPlanDisplayName(
            String planDisplayName
    ) {
        return command(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                planDisplayName,
                49900,
                "RUB",
                BillingPeriod.MONTH,
                null,
                EXPIRES_AT
        );
    }

    private PaymentCheckoutCreationCommand commandWithPrice(
            long priceMinor
    ) {
        return command(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Профессиональный",
                priceMinor,
                "RUB",
                BillingPeriod.MONTH,
                null,
                EXPIRES_AT
        );
    }

    private PaymentCheckoutCreationCommand commandWithCurrency(
            String currency
    ) {
        return command(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Профессиональный",
                49900,
                currency,
                BillingPeriod.MONTH,
                null,
                EXPIRES_AT
        );
    }

    private PaymentCheckoutCreationCommand command(
            UUID intentId,
            String offerCode,
            String planCode,
            String planDisplayName,
            long priceMinor,
            String currency,
            BillingPeriod billingPeriod,
            String externalProductId,
            Instant expiresAt
    ) {
        return new PaymentCheckoutCreationCommand(
                intentId,
                offerCode,
                planCode,
                planDisplayName,
                priceMinor,
                currency,
                billingPeriod,
                externalProductId,
                expiresAt
        );
    }
}
