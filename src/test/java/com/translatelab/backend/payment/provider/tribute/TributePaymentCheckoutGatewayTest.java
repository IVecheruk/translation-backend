package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TributePaymentCheckoutGatewayTest {

    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final UUID ORDER_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final URI WEBAPP_PAYMENT_URL = URI.create(
            "https://t.me/tribute/app?startapp=b2RK4mN"
    );
    private static final String CREATE_ORDER_URL =
            "https://tribute.tg/api/v1/shop/orders";

    private MockRestServiceServer server;
    private TributePaymentCheckoutGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://tribute.tg/api/v1")
                .defaultHeader("Api-Key", "synthetic-test-key")
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                );
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TributePaymentCheckoutGateway(
                builder.build(),
                new TributeCheckoutMapper()
        );
    }

    @Test
    void shouldExposeStableProviderCode() {
        assertEquals("TRIBUTE", gateway.providerCode());
    }

    @Test
    void shouldCreateOrderUsingExactTributeHttpContract() {
        server.expect(once(), requestTo(CREATE_ORDER_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Api-Key",
                        "synthetic-test-key"
                ))
                .andExpect(header(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(content().json("""
                        {
                          "amount": 49900,
                          "currency": "rub",
                          "title": "Профессиональный",
                          "description": "Подписка «Профессиональный» на один месяц",
                          "customerId": "00000000-0000-0000-0000-000000000123",
                          "period": "monthly"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "uuid": "550e8400-e29b-41d4-a716-446655440000",
                          "paymentUrl": "https://web.tribute.tg/shop/pay/550e8400-e29b-41d4-a716-446655440000",
                          "webappPaymentUrl": "https://t.me/tribute/app?startapp=b2RK4mN"
                        }
                        """, MediaType.APPLICATION_JSON));

        PaymentCheckoutResult result = gateway.createCheckout(
                validCommand("RUB")
        );

        server.verify();
        assertAll(
                () -> assertEquals(
                        ORDER_ID.toString(),
                        result.externalCheckoutId()
                ),
                () -> assertEquals(
                        WEBAPP_PAYMENT_URL,
                        result.redirectUrl()
                )
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 429, 500, 503})
    void shouldMapTributeHttpFailureToSafeProviderException(
            int statusCode
    ) {
        server.expect(once(), requestTo(CREATE_ORDER_URL))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode)));

        PaymentProviderUnavailableException exception = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> gateway.createCheckout(validCommand("RUB"))
        );

        server.verify();
        assertAll(
                () -> assertEquals(
                        "Платёжный сервис временно недоступен",
                        exception.getMessage()
                ),
                () -> assertInstanceOf(
                        RestClientException.class,
                        exception.getCause()
                )
        );
    }

    @Test
    void shouldMapMalformedJsonToSafeProviderException() {
        server.expect(once(), requestTo(CREATE_ORDER_URL))
                .andRespond(withSuccess(
                        "{malformed-json",
                        MediaType.APPLICATION_JSON
                ));

        PaymentProviderUnavailableException exception = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> gateway.createCheckout(validCommand("RUB"))
        );

        server.verify();
        assertInstanceOf(
                RestClientException.class,
                exception.getCause()
        );
    }

    @Test
    void shouldMapUnsafeProviderResponseToSafeProviderException() {
        server.expect(once(), requestTo(CREATE_ORDER_URL))
                .andRespond(withSuccess("""
                        {
                          "uuid": "550e8400-e29b-41d4-a716-446655440000",
                          "paymentUrl": "https://web.tribute.tg/shop/pay/550e8400-e29b-41d4-a716-446655440000",
                          "webappPaymentUrl": "https://t.me.evil.example/tribute/app"
                        }
                        """, MediaType.APPLICATION_JSON));

        PaymentProviderUnavailableException exception = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> gateway.createCheckout(validCommand("RUB"))
        );

        server.verify();
        assertInstanceOf(
                RestClientException.class,
                exception.getCause()
        );
    }

    @Test
    void shouldRejectEmptySuccessfulResponse() {
        server.expect(once(), requestTo(CREATE_ORDER_URL))
                .andRespond(withSuccess());

        PaymentProviderUnavailableException exception = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> gateway.createCheckout(validCommand("RUB"))
        );

        server.verify();
        assertAll(
                () -> assertEquals(
                        "Платёжный сервис временно недоступен",
                        exception.getMessage()
                ),
                () -> assertNull(exception.getCause())
        );
    }

    @Test
    void shouldRejectNullCommandBeforeHttpCall() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> gateway.createCheckout(null)
        );

        assertEquals(
                "Команда создания checkout не должна быть null",
                exception.getMessage()
        );
        server.verify();
    }

    @Test
    void shouldKeepInternalMappingFailureOutsideProviderErrorHandling() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> gateway.createCheckout(validCommand("GBP"))
        );

        assertEquals(
                "Валюта платёжного предложения "
                        + "не поддерживается Tribute",
                exception.getMessage()
        );
        server.verify();
    }

    private PaymentCheckoutCreationCommand validCommand(
            String currency
    ) {
        return new PaymentCheckoutCreationCommand(
                INTENT_ID,
                "PRO_TRIBUTE_MONTH",
                "PRO",
                "Профессиональный",
                49_900L,
                currency,
                BillingPeriod.MONTH,
                null,
                Instant.parse("2026-09-01T00:30:00Z")
        );
    }
}
