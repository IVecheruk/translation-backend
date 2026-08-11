package com.translatelab.backend.payment.provider.tribute.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TributeCreateOrderResponseTest {

    private static final UUID ORDER_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final URI PAYMENT_URL = URI.create(
            "https://web.tribute.tg/shop/pay/"
                    + "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final URI WEBAPP_PAYMENT_URL = URI.create(
            "https://t.me/tribute/app?startapp=b2RK4mN"
    );

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldPreserveValidValues() {
        TributeCreateOrderResponse response = validResponse();

        assertAll(
                () -> assertEquals(ORDER_ID, response.uuid()),
                () -> assertEquals(PAYMENT_URL, response.paymentUrl()),
                () -> assertEquals(
                        WEBAPP_PAYMENT_URL,
                        response.webappPaymentUrl()
                )
        );
    }

    @Test
    void shouldDeserializeOfficialTributeResponse() throws Exception {
        String json = """
                {
                  "uuid": "550e8400-e29b-41d4-a716-446655440000",
                  "paymentUrl": "https://web.tribute.tg/shop/pay/550e8400-e29b-41d4-a716-446655440000",
                  "webappPaymentUrl": "https://t.me/tribute/app?startapp=b2RK4mN"
                }
                """;

        TributeCreateOrderResponse response = objectMapper.readValue(
                json,
                TributeCreateOrderResponse.class
        );

        assertAll(
                () -> assertEquals(ORDER_ID, response.uuid()),
                () -> assertEquals(PAYMENT_URL, response.paymentUrl()),
                () -> assertEquals(
                        WEBAPP_PAYMENT_URL,
                        response.webappPaymentUrl()
                )
        );
    }

    @Test
    void shouldIgnoreUnknownFutureResponseFields() throws Exception {
        String json = """
                {
                  "uuid": "550e8400-e29b-41d4-a716-446655440000",
                  "paymentUrl": "https://web.tribute.tg/shop/pay/550e8400-e29b-41d4-a716-446655440000",
                  "webappPaymentUrl": "https://t.me/tribute/app?startapp=b2RK4mN",
                  "futureField": "future-value",
                  "nestedFutureField": {
                    "enabled": true
                  }
                }
                """;

        TributeCreateOrderResponse response = objectMapper.readValue(
                json,
                TributeCreateOrderResponse.class
        );

        assertEquals(ORDER_ID, response.uuid());
    }

    @Test
    void shouldRejectNullOrderId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        null,
                        PAYMENT_URL,
                        WEBAPP_PAYMENT_URL
                )
        );

        assertEquals(
                "Идентификатор заказа Tribute не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldAcceptCaseInsensitiveHttpsSchemeAndTrustedHost(
            boolean paymentUrlField
    ) {
        URI url = URI.create(
                paymentUrlField
                        ? "HTTPS://WEB.TRIBUTE.TG/shop/pay/123"
                        : "HTTPS://T.ME/tribute/app?startapp=123"
        );

        TributeCreateOrderResponse response = responseWithUrl(
                paymentUrlField,
                url
        );

        assertEquals(
                url,
                paymentUrlField
                        ? response.paymentUrl()
                        : response.webappPaymentUrl()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectNullUrl(boolean paymentUrlField) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(paymentUrlField, null)
        );

        assertEquals(
                fieldName(paymentUrlField) + " не должна быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectRelativeUrl(boolean paymentUrlField) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(
                        paymentUrlField,
                        URI.create("/checkout/123")
                )
        );

        assertEquals(
                fieldName(paymentUrlField) + " должна быть абсолютной",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectNonHttpsUrl(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "http://web.tribute.tg/shop/pay/123"
                        : "http://t.me/tribute/app?startapp=123"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(paymentUrlField, url)
        );

        assertEquals(
                fieldName(paymentUrlField) + " должна использовать HTTPS",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectUntrustedOrLookalikeHost(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "https://web.tribute.tg.evil.example/pay/123"
                        : "https://t.me.evil.example/tribute/app"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(paymentUrlField, url)
        );

        assertEquals(
                fieldName(paymentUrlField) + " содержит недоверенный домен",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectUrlWithUserInfo(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "https://user@web.tribute.tg/shop/pay/123"
                        : "https://user@t.me/tribute/app"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(paymentUrlField, url)
        );

        assertEquals(
                fieldName(paymentUrlField)
                        + " не должна содержать учётные данные",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectNonStandardHttpsPort(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "https://web.tribute.tg:8443/shop/pay/123"
                        : "https://t.me:8443/tribute/app"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(paymentUrlField, url)
        );

        assertEquals(
                fieldName(paymentUrlField)
                        + " должна использовать стандартный HTTPS-порт",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldAcceptExplicitStandardHttpsPort(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "https://web.tribute.tg:443/shop/pay/123"
                        : "https://t.me:443/tribute/app?startapp=123"
        );

        TributeCreateOrderResponse response = responseWithUrl(
                paymentUrlField,
                url
        );

        assertEquals(
                url,
                paymentUrlField
                        ? response.paymentUrl()
                        : response.webappPaymentUrl()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectUrlWithoutMeaningfulPath(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "https://web.tribute.tg/"
                        : "https://t.me/?startapp=123"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(paymentUrlField, url)
        );

        assertEquals(
                fieldName(paymentUrlField) + " должна содержать путь",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectUrlWithFragment(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "https://web.tribute.tg/shop/pay/123#fragment"
                        : "https://t.me/tribute/app?startapp=123#fragment"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(paymentUrlField, url)
        );

        assertEquals(
                fieldName(paymentUrlField)
                        + " не должна содержать фрагмент",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldAllowQueryParameters(boolean paymentUrlField) {
        URI url = URI.create(
                paymentUrlField
                        ? "https://web.tribute.tg/shop/pay/123?source=api"
                        : "https://t.me/tribute/app?startapp=123"
        );

        TributeCreateOrderResponse response = responseWithUrl(
                paymentUrlField,
                url
        );

        assertEquals(
                url,
                paymentUrlField
                        ? response.paymentUrl()
                        : response.webappPaymentUrl()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldAcceptUrlAtMaximumLength(boolean paymentUrlField) {
        URI url = urlWithLength(paymentUrlField, 2048);

        TributeCreateOrderResponse response = responseWithUrl(
                paymentUrlField,
                url
        );

        assertEquals(
                2048,
                (paymentUrlField
                        ? response.paymentUrl()
                        : response.webappPaymentUrl())
                        .toString()
                        .length()
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectUrlAboveMaximumLength(boolean paymentUrlField) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> responseWithUrl(
                        paymentUrlField,
                        urlWithLength(paymentUrlField, 2049)
                )
        );

        assertEquals(
                fieldName(paymentUrlField)
                        + " не должна превышать 2048 символов",
                exception.getMessage()
        );
    }

    private TributeCreateOrderResponse validResponse() {
        return response(
                ORDER_ID,
                PAYMENT_URL,
                WEBAPP_PAYMENT_URL
        );
    }

    private TributeCreateOrderResponse responseWithUrl(
            boolean paymentUrlField,
            URI url
    ) {
        return response(
                ORDER_ID,
                paymentUrlField ? url : PAYMENT_URL,
                paymentUrlField ? WEBAPP_PAYMENT_URL : url
        );
    }

    private TributeCreateOrderResponse response(
            UUID uuid,
            URI paymentUrl,
            URI webappPaymentUrl
    ) {
        return new TributeCreateOrderResponse(
                uuid,
                paymentUrl,
                webappPaymentUrl
        );
    }

    private String fieldName(boolean paymentUrlField) {
        return paymentUrlField
                ? "Ссылка оплаты Tribute"
                : "Telegram-ссылка оплаты Tribute";
    }

    private URI urlWithLength(
            boolean paymentUrlField,
            int length
    ) {
        String prefix = paymentUrlField
                ? "https://web.tribute.tg/"
                : "https://t.me/";

        return URI.create(prefix + "a".repeat(length - prefix.length()));
    }
}
