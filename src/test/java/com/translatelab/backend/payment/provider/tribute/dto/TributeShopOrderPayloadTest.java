package com.translatelab.backend.payment.provider.tribute.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TributeShopOrderPayloadTest {

    private static final UUID ORDER_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldDeserializeOfficialShopOrderPayload() throws Exception {
        TributeShopOrderPayload payload = objectMapper.readValue(
                """
                        {
                          "uuid": "550e8400-e29b-41d4-a716-446655440000",
                          "amount": 100000,
                          "currency": "rub",
                          "fee": 8000,
                          "status": "paid",
                          "email": "private@example.test",
                          "isRecurrent": false,
                          "period": "monthly",
                          "paymentToken": "synthetic-payment-token",
                          "cardLast4": "4242",
                          "cardBrand": "VISA"
                        }
                        """,
                TributeShopOrderPayload.class
        );

        assertAll(
                () -> assertEquals(ORDER_ID, payload.uuid()),
                () -> assertEquals(100_000L, payload.amount()),
                () -> assertEquals("rub", payload.currency()),
                () -> assertEquals("paid", payload.status()),
                () -> assertFalse(payload.recurrent()),
                () -> assertEquals("monthly", payload.period())
        );
    }

    @Test
    void shouldIgnoreSensitiveAndFutureProviderFields() throws Exception {
        TributeShopOrderPayload payload = objectMapper.readValue(
                """
                        {
                          "uuid": "550e8400-e29b-41d4-a716-446655440000",
                          "amount": 100000,
                          "currency": "rub",
                          "status": "paid",
                          "isRecurrent": true,
                          "period": "monthly",
                          "email": "private@example.test",
                          "paymentToken": "synthetic-payment-token",
                          "cardLast4": "4242",
                          "futureField": {
                            "enabled": true
                          }
                        }
                        """,
                TributeShopOrderPayload.class
        );

        assertTrue(payload.recurrent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eur", "rub", "usd"})
    void shouldAcceptEverySupportedCurrency(String currency) {
        TributeShopOrderPayload payload = payload(
                ORDER_ID,
                1L,
                currency,
                "paid",
                false,
                "monthly"
        );

        assertEquals(currency, payload.currency());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPreserveRecurrenceMarker(boolean recurrent) {
        TributeShopOrderPayload payload = payload(
                ORDER_ID,
                1L,
                "rub",
                "paid",
                recurrent,
                "monthly"
        );

        assertEquals(recurrent, payload.recurrent());
    }

    @Test
    void shouldAcceptMaximumPositiveAmount() {
        TributeShopOrderPayload payload = payload(
                ORDER_ID,
                Long.MAX_VALUE,
                "rub",
                "paid",
                false,
                "monthly"
        );

        assertEquals(Long.MAX_VALUE, payload.amount());
    }

    @Test
    void shouldRejectMissingOrderId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> payload(
                        null,
                        1L,
                        "rub",
                        "paid",
                        false,
                        "monthly"
                )
        );

        assertEquals(
                "Идентификатор заказа Tribute не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void shouldRejectNonPositiveAmount(long amount) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> payload(
                        ORDER_ID,
                        amount,
                        "rub",
                        "paid",
                        false,
                        "monthly"
                )
        );

        assertEquals(
                "Сумма заказа Tribute должна быть больше нуля",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "RUB", "gbp", "rur"})
    void shouldRejectUnsupportedCurrency(String currency) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> payload(
                        ORDER_ID,
                        1L,
                        currency,
                        "paid",
                        false,
                        "monthly"
                )
        );

        assertEquals(
                "Валюта заказа Tribute не поддерживается",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "PAID", "pending", "failed"})
    void shouldRejectNonPaidStatus(String status) {
        assertThrows(
                IllegalArgumentException.class,
                () -> payload(
                        ORDER_ID,
                        1L,
                        "rub",
                        status,
                        false,
                        "monthly"
                )
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "MONTHLY", "weekly", "yearly"})
    void shouldRejectNonMonthlyPeriod(String period) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> payload(
                        ORDER_ID,
                        1L,
                        "rub",
                        "paid",
                        false,
                        period
                )
        );

        assertEquals(
                "Заказ Tribute должен иметь месячный период",
                exception.getMessage()
        );
    }

    private TributeShopOrderPayload payload(
            UUID uuid,
            long amount,
            String currency,
            String status,
            boolean recurrent,
            String period
    ) {
        return new TributeShopOrderPayload(
                uuid,
                amount,
                currency,
                status,
                recurrent,
                period
        );
    }
}
