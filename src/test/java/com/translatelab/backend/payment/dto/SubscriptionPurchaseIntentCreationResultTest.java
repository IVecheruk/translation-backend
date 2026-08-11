package com.translatelab.backend.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionPurchaseIntentCreationResultTest {

    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
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
        SubscriptionPurchaseIntentCreationResult result =
                new SubscriptionPurchaseIntentCreationResult(
                        INTENT_ID,
                        "TRIBUTE",
                        EXPIRES_AT
                );

        assertAll(
                () -> assertEquals(INTENT_ID, result.intentId()),
                () -> assertEquals("TRIBUTE", result.provider()),
                () -> assertEquals(EXPIRES_AT, result.expiresAt())
        );
    }

    @Test
    void shouldSerializeAccordingToPurchaseStartApiContract()
            throws Exception {
        SubscriptionPurchaseIntentCreationResult result =
                new SubscriptionPurchaseIntentCreationResult(
                        INTENT_ID,
                        "TRIBUTE",
                        EXPIRES_AT
                );

        String json = objectMapper.writeValueAsString(result);

        assertEquals(
                "{\"intent_id\":"
                        + "\"00000000-0000-0000-0000-000000000123\","
                        + "\"provider\":\"TRIBUTE\","
                        + "\"expires_at\":"
                        + "\"2026-09-01T00:30:00Z\"}",
                json
        );
    }

    @Test
    void shouldRejectNullIntentId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCreationResult(
                        null,
                        "TRIBUTE",
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
        SubscriptionPurchaseIntentCreationResult result =
                new SubscriptionPurchaseIntentCreationResult(
                        INTENT_ID,
                        provider,
                        EXPIRES_AT
                );

        assertEquals(provider, result.provider());
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
                () -> new SubscriptionPurchaseIntentCreationResult(
                        INTENT_ID,
                        provider,
                        EXPIRES_AT
                )
        );

        assertEquals(
                "Некорректный формат кода платёжного провайдера",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullExpiration() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SubscriptionPurchaseIntentCreationResult(
                        INTENT_ID,
                        "TRIBUTE",
                        null
                )
        );

        assertEquals(
                "Срок действия заявки не должен быть null",
                exception.getMessage()
        );
    }
}
