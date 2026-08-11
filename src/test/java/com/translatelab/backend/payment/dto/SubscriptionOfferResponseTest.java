package com.translatelab.backend.payment.dto;

import com.translatelab.backend.payment.entity.BillingPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionOfferResponseTest {

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldPreserveValidValuesAndNormalizeDisplayName() {
        SubscriptionOfferResponse response = response(
                "PRO",
                "  Профессиональный  ",
                99900,
                "RUB",
                BillingPeriod.MONTH
        );

        assertAll(
                () -> assertEquals("PRO", response.planCode()),
                () -> assertEquals(
                        "Профессиональный",
                        response.planDisplayName()
                ),
                () -> assertEquals(99900L, response.priceMinor()),
                () -> assertEquals("RUB", response.currency()),
                () -> assertEquals(
                        BillingPeriod.MONTH,
                        response.billingPeriod()
                )
        );
    }

    @Test
    void shouldSerializeAccordingToPublicCatalogContract()
            throws Exception {
        SubscriptionOfferResponse response = validResponse();

        String json = objectMapper.writeValueAsString(response);

        assertEquals(
                "{\"plan_code\":\"PRO\","
                        + "\"plan_display_name\":\"Профессиональный\","
                        + "\"price_minor\":99900,"
                        + "\"currency\":\"RUB\","
                        + "\"billing_period\":\"MONTH\"}",
                json
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "PRO_1",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidPlanCodeBoundaries(String planCode) {
        SubscriptionOfferResponse response = response(
                planCode,
                "Тариф",
                1,
                "RUB",
                BillingPeriod.MONTH
        );

        assertEquals(planCode, response.planCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "pro",
            "1PRO",
            "PRO-PLUS",
            "PRO PLUS",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidPlanCode(String planCode) {
        assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        planCode,
                        "Тариф",
                        1,
                        "RUB",
                        BillingPeriod.MONTH
                )
        );
    }

    @Test
    void shouldAcceptDisplayNameAtMaximumLength() {
        String displayName = "я".repeat(100);

        SubscriptionOfferResponse response = response(
                "PRO",
                displayName,
                1,
                "RUB",
                BillingPeriod.MONTH
        );

        assertEquals(displayName, response.planDisplayName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectMissingDisplayName(String displayName) {
        assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        "PRO",
                        displayName,
                        1,
                        "RUB",
                        BillingPeriod.MONTH
                )
        );
    }

    @Test
    void shouldRejectDisplayNameLongerThanDatabaseColumn() {
        assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        "PRO",
                        "я".repeat(101),
                        1,
                        "RUB",
                        BillingPeriod.MONTH
                )
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void shouldAcceptPositivePrice(long priceMinor) {
        SubscriptionOfferResponse response = response(
                "PRO",
                "Тариф",
                priceMinor,
                "RUB",
                BillingPeriod.MONTH
        );

        assertEquals(priceMinor, response.priceMinor());
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0})
    void shouldRejectNonPositivePrice(long priceMinor) {
        assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        "PRO",
                        "Тариф",
                        priceMinor,
                        "RUB",
                        BillingPeriod.MONTH
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUB", "USD"})
    void shouldAcceptIsoStyleCurrency(String currency) {
        SubscriptionOfferResponse response = response(
                "PRO",
                "Тариф",
                1,
                currency,
                BillingPeriod.MONTH
        );

        assertEquals(currency, response.currency());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "rub", "RU", "RUB1", "R1B"})
    void shouldRejectInvalidCurrency(String currency) {
        assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        "PRO",
                        "Тариф",
                        1,
                        currency,
                        BillingPeriod.MONTH
                )
        );
    }

    @Test
    void shouldRejectMissingBillingPeriod() {
        assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        "PRO",
                        "Тариф",
                        1,
                        "RUB",
                        null
                )
        );
    }

    private SubscriptionOfferResponse validResponse() {
        return response(
                "PRO",
                "Профессиональный",
                99900,
                "RUB",
                BillingPeriod.MONTH
        );
    }

    private SubscriptionOfferResponse response(
            String planCode,
            String planDisplayName,
            long priceMinor,
            String currency,
            BillingPeriod billingPeriod
    ) {
        return new SubscriptionOfferResponse(
                planCode,
                planDisplayName,
                priceMinor,
                currency,
                billingPeriod
        );
    }
}
