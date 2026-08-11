package com.translatelab.backend.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentCheckoutResultTest {

    private static final URI VALID_REDIRECT_URL = URI.create(
            "https://t.me/translatelab_bot?start=payload"
    );

    @Test
    void shouldNormalizeCheckoutIdAndPreserveRedirectUrl() {
        PaymentCheckoutResult result = new PaymentCheckoutResult(
                "  checkout-123  ",
                VALID_REDIRECT_URL
        );

        assertAll(
                () -> assertEquals(
                        "checkout-123",
                        result.externalCheckoutId()
                ),
                () -> assertSame(
                        VALID_REDIRECT_URL,
                        result.redirectUrl()
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void shouldRejectEmptyCheckoutId(String externalCheckoutId) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult(
                        externalCheckoutId,
                        VALID_REDIRECT_URL
                )
        );

        assertEquals(
                "Внешний идентификатор checkout не должен быть пустым",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullCheckoutId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult(
                        null,
                        VALID_REDIRECT_URL
                )
        );

        assertEquals(
                "Внешний идентификатор checkout не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptCheckoutIdAtMaximumLength() {
        String externalCheckoutId = "a".repeat(255);

        PaymentCheckoutResult result = new PaymentCheckoutResult(
                externalCheckoutId,
                VALID_REDIRECT_URL
        );

        assertEquals(
                externalCheckoutId,
                result.externalCheckoutId()
        );
    }

    @Test
    void shouldRejectCheckoutIdAboveMaximumLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult(
                        "a".repeat(256),
                        VALID_REDIRECT_URL
                )
        );

        assertEquals(
                "Внешний идентификатор checkout "
                        + "не должен превышать 255 символов",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullRedirectUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult("checkout-123", null)
        );

        assertEquals(
                "Ссылка перенаправления не должна быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRelativeRedirectUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult(
                        "checkout-123",
                        URI.create("/checkout/123")
                )
        );

        assertEquals(
                "Ссылка перенаправления должна быть абсолютной",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/checkout",
            "ftp://example.com/checkout",
            "tg://resolve?domain=translatelab_bot"
    })
    void shouldRejectNonHttpsRedirectUrl(String redirectUrl) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult(
                        "checkout-123",
                        URI.create(redirectUrl)
                )
        );

        assertEquals(
                "Ссылка перенаправления должна использовать HTTPS",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptUppercaseHttpsScheme() {
        URI redirectUrl = URI.create(
                "HTTPS://t.me/translatelab_bot?start=payload"
        );

        PaymentCheckoutResult result = new PaymentCheckoutResult(
                "checkout-123",
                redirectUrl
        );

        assertSame(redirectUrl, result.redirectUrl());
    }

    @Test
    void shouldRejectHttpsRedirectWithoutHost() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult(
                        "checkout-123",
                        URI.create("https:checkout")
                )
        );

        assertEquals(
                "Ссылка перенаправления должна содержать хост",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptRedirectUrlAtMaximumLength() {
        URI redirectUrl = redirectUrlWithLength(2048);

        PaymentCheckoutResult result = new PaymentCheckoutResult(
                "checkout-123",
                redirectUrl
        );

        assertEquals(2048, result.redirectUrl().toString().length());
    }

    @Test
    void shouldRejectRedirectUrlAboveMaximumLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCheckoutResult(
                        "checkout-123",
                        redirectUrlWithLength(2049)
                )
        );

        assertEquals(
                "Ссылка перенаправления "
                        + "не должна превышать 2048 символов",
                exception.getMessage()
        );
    }

    private URI redirectUrlWithLength(int length) {
        String prefix = "https://example.com/";

        return URI.create(prefix + "a".repeat(length - prefix.length()));
    }
}
