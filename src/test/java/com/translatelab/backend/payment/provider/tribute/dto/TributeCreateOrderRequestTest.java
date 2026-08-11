package com.translatelab.backend.payment.provider.tribute.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TributeCreateOrderRequestTest {

    private static final UUID CUSTOMER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldPreserveValidValues() {
        TributeCreateOrderRequest request = validRequest();

        assertAll(
                () -> assertEquals(49_900L, request.amount()),
                () -> assertEquals("rub", request.currency()),
                () -> assertEquals(
                        "Профессиональный",
                        request.title()
                ),
                () -> assertEquals(
                        "Подписка TranslateLab на один месяц",
                        request.description()
                ),
                () -> assertEquals(CUSTOMER_ID, request.customerId()),
                () -> assertEquals("monthly", request.period())
        );
    }

    @Test
    void shouldSerializeExactTributeContract() throws Exception {
        String json = objectMapper.writeValueAsString(validRequest());

        assertEquals(
                "{\"amount\":49900,"
                        + "\"currency\":\"rub\","
                        + "\"title\":\"Профессиональный\","
                        + "\"description\":"
                        + "\"Подписка TranslateLab на один месяц\","
                        + "\"customerId\":"
                        + "\"00000000-0000-0000-0000-000000000123\","
                        + "\"period\":\"monthly\"}",
                json
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 49_900L, Long.MAX_VALUE})
    void shouldAcceptPositiveAmount(long amount) {
        TributeCreateOrderRequest request = request(
                amount,
                "rub",
                "Тариф",
                "Описание",
                CUSTOMER_ID,
                "monthly"
        );

        assertEquals(amount, request.amount());
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L})
    void shouldRejectNonPositiveAmount(long amount) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        amount,
                        "rub",
                        "Тариф",
                        "Описание",
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Сумма заказа должна быть больше нуля",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"eur", "rub", "usd"})
    void shouldAcceptSupportedLowercaseCurrency(String currency) {
        TributeCreateOrderRequest request = request(
                1L,
                currency,
                "Тариф",
                "Описание",
                CUSTOMER_ID,
                "monthly"
        );

        assertEquals(currency, request.currency());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "RUB", "Rub", "gbp", "ruble"})
    void shouldRejectUnsupportedOrNonCanonicalCurrency(String currency) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        currency,
                        "Тариф",
                        "Описание",
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Tribute поддерживает валюты eur, rub и usd "
                        + "в нижнем регистре",
                exception.getMessage()
        );
    }

    @Test
    void shouldNormalizeRequiredText() {
        TributeCreateOrderRequest request = request(
                1L,
                "rub",
                " \tТариф\r\n",
                "\n Описание \t",
                CUSTOMER_ID,
                "monthly"
        );

        assertAll(
                () -> assertEquals("Тариф", request.title()),
                () -> assertEquals("Описание", request.description())
        );
    }

    @Test
    void shouldRejectNullTitle() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        null,
                        "Описание",
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Название заказа не должно быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\r\n"})
    void shouldRejectBlankTitle(String title) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        title,
                        "Описание",
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Название заказа не должно быть пустым",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptTitleAtMaximumUtf16Length() {
        String title = "😀".repeat(50);

        TributeCreateOrderRequest request = request(
                1L,
                "rub",
                title,
                "Описание",
                CUSTOMER_ID,
                "monthly"
        );

        assertEquals(100, request.title().length());
    }

    @Test
    void shouldRejectTitleAboveMaximumUtf16Length() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        "😀".repeat(51),
                        "Описание",
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Название заказа не должно превышать 100 символов",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullDescription() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        "Тариф",
                        null,
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Описание заказа не должно быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\r\n"})
    void shouldRejectBlankDescription(String description) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        "Тариф",
                        description,
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Описание заказа не должно быть пустым",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptDescriptionAtMaximumUtf16Length() {
        String description = "😀".repeat(150);

        TributeCreateOrderRequest request = request(
                1L,
                "rub",
                "Тариф",
                description,
                CUSTOMER_ID,
                "monthly"
        );

        assertEquals(300, request.description().length());
    }

    @Test
    void shouldRejectDescriptionAboveMaximumUtf16Length() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        "Тариф",
                        "😀".repeat(151),
                        CUSTOMER_ID,
                        "monthly"
                )
        );

        assertEquals(
                "Описание заказа не должно превышать 300 символов",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullCustomerId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        "Тариф",
                        "Описание",
                        null,
                        "monthly"
                )
        );

        assertEquals(
                "Идентификатор клиента не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Monthly", "MONTHLY", "yearly"})
    void shouldRejectAnyPeriodOtherThanMonthly(String period) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        1L,
                        "rub",
                        "Тариф",
                        "Описание",
                        CUSTOMER_ID,
                        period
                )
        );

        assertEquals(
                "Период заказа Tribute должен быть monthly",
                exception.getMessage()
        );
    }

    private TributeCreateOrderRequest validRequest() {
        return request(
                49_900L,
                "rub",
                "Профессиональный",
                "Подписка TranslateLab на один месяц",
                CUSTOMER_ID,
                "monthly"
        );
    }

    private TributeCreateOrderRequest request(
            long amount,
            String currency,
            String title,
            String description,
            UUID customerId,
            String period
    ) {
        return new TributeCreateOrderRequest(
                amount,
                currency,
                title,
                description,
                customerId,
                period
        );
    }
}
