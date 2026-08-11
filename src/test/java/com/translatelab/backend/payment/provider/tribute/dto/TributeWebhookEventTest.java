package com.translatelab.backend.payment.provider.tribute.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TributeWebhookEventTest {

    private static final Instant CREATED_AT = Instant.parse(
            "2025-03-20T01:15:58.332460Z"
    );
    private static final Instant SENT_AT = Instant.parse(
            "2025-03-20T01:15:58.542279448Z"
    );

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldDeserializeOfficialShopOrderEnvelope() throws Exception {
        TributeWebhookEvent event = objectMapper.readValue(
                """
                        {
                          "name": "shop_order",
                          "created_at": "2025-03-20T01:15:58.332460Z",
                          "sent_at": "2025-03-20T01:15:58.542279448Z",
                          "payload": {
                            "uuid": "550e8400-e29b-41d4-a716-446655440000",
                            "amount": 100000,
                            "currency": "rub",
                            "status": "paid",
                            "email": "private@example.test",
                            "paymentToken": "synthetic-payment-token",
                            "cardLast4": "4242"
                          }
                        }
                        """,
                TributeWebhookEvent.class
        );

        assertAll(
                () -> assertEquals("shop_order", event.name()),
                () -> assertEquals(CREATED_AT, event.createdAt()),
                () -> assertEquals(SENT_AT, event.sentAt()),
                () -> assertTrue(event.payload().isObject()),
                () -> assertEquals(
                        "550e8400-e29b-41d4-a716-446655440000",
                        event.payload().get("uuid").asText()
                ),
                () -> assertEquals(
                        100_000L,
                        event.payload().get("amount").asLong()
                )
        );
    }

    @Test
    void shouldIgnoreUnknownFutureEnvelopeFields() throws Exception {
        TributeWebhookEvent event = objectMapper.readValue(
                """
                        {
                          "name": "shop_order",
                          "created_at": "2025-03-20T01:15:58.332460Z",
                          "sent_at": "2025-03-20T01:15:58.542279448Z",
                          "payload": {},
                          "delivery_attempt": 2,
                          "future_metadata": {
                            "source": "tribute"
                          }
                        }
                        """,
                TributeWebhookEvent.class
        );

        assertEquals("shop_order", event.name());
        assertTrue(event.payload().isObject());
    }

    @Test
    void shouldNormalizeEventName() {
        TributeWebhookEvent event = event(
                "  shop_order  ",
                CREATED_AT,
                SENT_AT,
                objectPayload()
        );

        assertEquals("shop_order", event.name());
    }

    @Test
    void shouldAcceptEventNameAtMaximumLength() {
        String name = "a" + "1_".repeat(31) + "b";

        TributeWebhookEvent event = event(
                name,
                CREATED_AT,
                SENT_AT,
                objectPayload()
        );

        assertEquals(64, event.name().length());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            " ",
            "Shop_order",
            "1_shop_order",
            "shop-order",
            "shop.order",
            "a1234567890123456789012345678901234567890123456789012345678901234"
    })
    void shouldRejectInvalidEventName(String name) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> event(
                        name,
                        CREATED_AT,
                        SENT_AT,
                        objectPayload()
                )
        );

        assertTrue(exception.getMessage().contains(
                "события Tribute"
        ));
    }

    @Test
    void shouldAcceptEqualCreationAndSendingTime() {
        TributeWebhookEvent event = event(
                "shop_order",
                CREATED_AT,
                CREATED_AT,
                objectPayload()
        );

        assertEquals(CREATED_AT, event.sentAt());
    }

    @Test
    void shouldRejectMissingCreationTime() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> event(
                        "shop_order",
                        null,
                        SENT_AT,
                        objectPayload()
                )
        );

        assertEquals(
                "Время создания события Tribute не должно быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingSendingTime() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> event(
                        "shop_order",
                        CREATED_AT,
                        null,
                        objectPayload()
                )
        );

        assertEquals(
                "Время отправки события Tribute не должно быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSendingTimeBeforeCreationTime() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> event(
                        "shop_order",
                        CREATED_AT,
                        CREATED_AT.minusNanos(1),
                        objectPayload()
                )
        );

        assertEquals(
                "Событие Tribute не может быть отправлено "
                        + "раньше времени его создания",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptEmptyObjectPayload() {
        TributeWebhookEvent event = event(
                "shop_order",
                CREATED_AT,
                SENT_AT,
                objectPayload()
        );

        assertTrue(event.payload().isObject());
        assertFalse(event.payload().properties().iterator().hasNext());
    }

    @Test
    void shouldRejectMissingPayload() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> event(
                        "shop_order",
                        CREATED_AT,
                        SENT_AT,
                        null
                )
        );

        assertEquals(
                "Payload события Tribute не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectJsonNullPayload() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> event(
                        "shop_order",
                        CREATED_AT,
                        SENT_AT,
                        objectMapper.nullNode()
                )
        );

        assertEquals(
                "Payload события Tribute не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectArrayPayload() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> event(
                        "shop_order",
                        CREATED_AT,
                        SENT_AT,
                        objectMapper.createArrayNode()
                )
        );

        assertEquals(
                "Payload события Tribute должен быть JSON-объектом",
                exception.getMessage()
        );
    }

    private TributeWebhookEvent event(
            String name,
            Instant createdAt,
            Instant sentAt,
            JsonNode payload
    ) {
        return new TributeWebhookEvent(
                name,
                createdAt,
                sentAt,
                payload
        );
    }

    private JsonNode objectPayload() {
        return objectMapper.createObjectNode();
    }
}
