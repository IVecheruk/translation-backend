package com.translatelab.backend.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionPurchaseStartResponseTest {

    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final URI REDIRECT_URL = URI.create(
            "https://t.me/translatelab_bot?start=payload"
    );
    private static final Instant EXPIRES_AT = Instant.parse(
            "2026-09-01T00:30:00Z"
    );

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldPreserveValidValues() {
        SubscriptionPurchaseStartResponse response = validResponse();

        assertAll(
                () -> assertEquals(INTENT_ID, response.intentId()),
                () -> assertEquals("TRIBUTE", response.provider()),
                () -> assertSame(
                        REDIRECT_URL,
                        response.redirectUrl()
                ),
                () -> assertEquals(EXPIRES_AT, response.expiresAt())
        );
    }

    @Test
    void shouldSerializeAccordingToPurchaseStartApiContract()
            throws Exception {
        String json = objectMapper.writeValueAsString(validResponse());

        assertEquals(
                "{\"intent_id\":"
                        + "\"00000000-0000-0000-0000-000000000123\","
                        + "\"provider\":\"TRIBUTE\","
                        + "\"redirect_url\":"
                        + "\"https://t.me/translatelab_bot?start=payload\","
                        + "\"expires_at\":"
                        + "\"2026-09-01T00:30:00Z\"}",
                json
        );
    }

    @Test
    void shouldRejectNullIntentId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        null,
                        "TRIBUTE",
                        REDIRECT_URL,
                        EXPIRES_AT
                )
        );

        assertEquals(
                "Идентификатор заявки не должен быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "TRIBUTE_1",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidProviderBoundaries(String provider) {
        SubscriptionPurchaseStartResponse response = response(
                INTENT_ID,
                provider,
                REDIRECT_URL,
                EXPIRES_AT
        );

        assertEquals(provider, response.provider());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "TRIBUTE-PAY",
            "TRIBUTE PAY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidProvider(String provider) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        INTENT_ID,
                        provider,
                        REDIRECT_URL,
                        EXPIRES_AT
                )
        );

        assertEquals(
                "Некорректный формат кода платёжного провайдера",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullRedirectUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        INTENT_ID,
                        "TRIBUTE",
                        null,
                        EXPIRES_AT
                )
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
                () -> response(
                        INTENT_ID,
                        "TRIBUTE",
                        URI.create("/checkout/123"),
                        EXPIRES_AT
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
                () -> response(
                        INTENT_ID,
                        "TRIBUTE",
                        URI.create(redirectUrl),
                        EXPIRES_AT
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

        SubscriptionPurchaseStartResponse response = response(
                INTENT_ID,
                "TRIBUTE",
                redirectUrl,
                EXPIRES_AT
        );

        assertSame(redirectUrl, response.redirectUrl());
    }

    @Test
    void shouldRejectHttpsRedirectWithoutHost() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        INTENT_ID,
                        "TRIBUTE",
                        URI.create("https:checkout"),
                        EXPIRES_AT
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

        SubscriptionPurchaseStartResponse response = response(
                INTENT_ID,
                "TRIBUTE",
                redirectUrl,
                EXPIRES_AT
        );

        assertEquals(2048, response.redirectUrl().toString().length());
    }

    @Test
    void shouldRejectRedirectUrlAboveMaximumLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        INTENT_ID,
                        "TRIBUTE",
                        redirectUrlWithLength(2049),
                        EXPIRES_AT
                )
        );

        assertEquals(
                "Ссылка перенаправления "
                        + "не должна превышать 2048 символов",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullExpiration() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> response(
                        INTENT_ID,
                        "TRIBUTE",
                        REDIRECT_URL,
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

        SubscriptionPurchaseStartResponse response = response(
                INTENT_ID,
                "TRIBUTE",
                REDIRECT_URL,
                historicalExpiration
        );

        assertEquals(historicalExpiration, response.expiresAt());
    }

    private SubscriptionPurchaseStartResponse validResponse() {
        return response(
                INTENT_ID,
                "TRIBUTE",
                REDIRECT_URL,
                EXPIRES_AT
        );
    }

    private SubscriptionPurchaseStartResponse response(
            UUID intentId,
            String provider,
            URI redirectUrl,
            Instant expiresAt
    ) {
        return new SubscriptionPurchaseStartResponse(
                intentId,
                provider,
                redirectUrl,
                expiresAt
        );
    }

    private URI redirectUrlWithLength(int length) {
        String prefix = "https://example.com/";

        return URI.create(prefix + "a".repeat(length - prefix.length()));
    }
}
