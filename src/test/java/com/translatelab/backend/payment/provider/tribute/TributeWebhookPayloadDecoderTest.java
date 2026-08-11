package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.provider.tribute.dto.TributeShopOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TributeWebhookPayloadDecoderTest {

    private static final UUID ORDER_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );
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
    void shouldDecodeValidatedShopOrderPayload() throws Exception {
        TributeWebhookPayloadDecoder decoder = decoder(objectMapper);
        TributeWebhookEvent event = event(
                "shop_order",
                objectMapper.readTree("""
                        {
                          "uuid": "550e8400-e29b-41d4-a716-446655440000",
                          "amount": 100000,
                          "currency": "rub",
                          "status": "paid",
                          "isRecurrent": false,
                          "period": "monthly",
                          "email": "private@example.test",
                          "paymentToken": "synthetic-payment-token"
                        }
                        """)
        );

        TributeShopOrderPayload result = decoder.decodeShopOrder(
                event
        );

        assertAll(
                () -> assertEquals(ORDER_ID, result.uuid()),
                () -> assertEquals(100_000L, result.amount()),
                () -> assertEquals("rub", result.currency()),
                () -> assertEquals("paid", result.status()),
                () -> assertEquals("monthly", result.period())
        );
    }

    @Test
    void shouldRejectUnsupportedEventBeforePayloadDecoding() {
        ObjectMapper mapper = mock(ObjectMapper.class);
        TributeWebhookPayloadDecoder decoder = decoder(mapper);
        TributeWebhookEvent event = event(
                "shop_order_charge_success",
                objectMapper.createObjectNode()
        );

        InvalidTributeWebhookException exception = assertThrows(
                InvalidTributeWebhookException.class,
                () -> decoder.decodeShopOrder(event)
        );

        assertAll(
                () -> assertEquals(
                        "Некорректные данные webhook Tribute",
                        exception.getMessage()
                ),
                () -> assertNull(exception.getCause())
        );
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldMapMalformedProviderValueToSafeException()
            throws Exception {
        TributeWebhookPayloadDecoder decoder = decoder(objectMapper);
        TributeWebhookEvent event = event(
                "shop_order",
                objectMapper.readTree("""
                        {
                          "uuid": "not-a-uuid",
                          "amount": 100000,
                          "currency": "rub",
                          "status": "paid",
                          "isRecurrent": false,
                          "period": "monthly"
                        }
                        """)
        );

        InvalidTributeWebhookException exception = assertThrows(
                InvalidTributeWebhookException.class,
                () -> decoder.decodeShopOrder(event)
        );

        assertAll(
                () -> assertEquals(
                        "Некорректные данные webhook Tribute",
                        exception.getMessage()
                ),
                () -> assertInstanceOf(
                        JacksonException.class,
                        exception.getCause()
                )
        );
    }

    @Test
    void shouldMapPayloadInvariantFailureToSafeException()
            throws Exception {
        TributeWebhookPayloadDecoder decoder = decoder(objectMapper);
        TributeWebhookEvent event = event(
                "shop_order",
                objectMapper.readTree("""
                        {
                          "uuid": "550e8400-e29b-41d4-a716-446655440000",
                          "amount": 0,
                          "currency": "rub",
                          "status": "paid",
                          "isRecurrent": false,
                          "period": "monthly"
                        }
                        """)
        );

        InvalidTributeWebhookException exception = assertThrows(
                InvalidTributeWebhookException.class,
                () -> decoder.decodeShopOrder(event)
        );

        assertEquals(
                "Некорректные данные webhook Tribute",
                exception.getMessage()
        );
        assertInstanceOf(
                JacksonException.class,
                exception.getCause()
        );
    }

    @Test
    void shouldMapDirectMapperFailureWithoutLeakingDetails() {
        ObjectMapper mapper = mock(ObjectMapper.class);
        TributeWebhookPayloadDecoder decoder = decoder(mapper);
        TributeWebhookEvent event = event(
                "shop_order",
                objectMapper.createObjectNode()
        );
        IllegalArgumentException cause = new IllegalArgumentException(
                "private@example.test synthetic-payment-token"
        );
        given(mapper.treeToValue(
                any(JsonNode.class),
                eq(TributeShopOrderPayload.class)
        )).willThrow(cause);

        InvalidTributeWebhookException exception = assertThrows(
                InvalidTributeWebhookException.class,
                () -> decoder.decodeShopOrder(event)
        );

        assertAll(
                () -> assertEquals(
                        "Некорректные данные webhook Tribute",
                        exception.getMessage()
                ),
                () -> assertSame(cause, exception.getCause())
        );
    }

    @Test
    void shouldRejectMissingEvent() {
        TributeWebhookPayloadDecoder decoder = decoder(objectMapper);

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> decoder.decodeShopOrder(null)
        );

        assertEquals(
                "Событие Tribute не должно быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingObjectMapper() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new TributeWebhookPayloadDecoder(null)
        );

        assertEquals(
                "ObjectMapper не должен быть null",
                exception.getMessage()
        );
    }

    private TributeWebhookPayloadDecoder decoder(
            ObjectMapper mapper
    ) {
        return new TributeWebhookPayloadDecoder(mapper);
    }

    private TributeWebhookEvent event(
            String name,
            JsonNode payload
    ) {
        return new TributeWebhookEvent(
                name,
                CREATED_AT,
                SENT_AT,
                payload
        );
    }
}
