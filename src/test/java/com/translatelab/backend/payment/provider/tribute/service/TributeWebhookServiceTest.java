package com.translatelab.backend.payment.provider.tribute.service;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookCommandMapper;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookPayloadDecoder;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookSignatureVerifier;
import com.translatelab.backend.payment.provider.tribute.dto.TributeShopOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookSignatureException;
import com.translatelab.backend.payment.service.SubscriptionPurchaseCompletionService;
import com.translatelab.backend.payment.service.SubscriptionProviderLifecycleService;
import com.translatelab.backend.payment.service.SubscriptionPurchaseFailureService;
import com.translatelab.backend.payment.entity.BillingPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TributeWebhookServiceTest {

    private static final byte[] RAW_BODY = "{\"name\":\"shop_order\"}"
            .getBytes(StandardCharsets.UTF_8);
    private static final String SIGNATURE = "a".repeat(64);
    private static final UUID ORDER_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final Instant PERIOD_START = Instant.parse(
            "2026-08-01T00:00:00Z"
    );
    private static final Instant PERIOD_END = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Mock
    private TributeWebhookSignatureVerifier signatureVerifier;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TributeWebhookPayloadDecoder payloadDecoder;
    @Mock
    private TributeWebhookCommandMapper commandMapper;
    @Mock
    private SubscriptionPurchaseCompletionService completionService;
    @Mock
    private SubscriptionProviderLifecycleService lifecycleService;
    @Mock
    private SubscriptionPurchaseFailureService purchaseFailureService;

    private TributeWebhookService service;
    private TributeWebhookEvent event;
    private TributeShopOrderPayload payload;
    private SubscriptionPurchaseCompletionCommand command;

    @BeforeEach
    void setUp() {
        service = new TributeWebhookService(
                signatureVerifier,
                objectMapper,
                payloadDecoder,
                commandMapper,
                completionService,
                lifecycleService,
                purchaseFailureService,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
        event = new TributeWebhookEvent(
                "shop_order",
                PERIOD_START,
                PERIOD_START.plusMillis(100),
                JsonMapper.builder().build().createObjectNode()
        );
        payload = new TributeShopOrderPayload(
                ORDER_ID,
                100_000L,
                "rub",
                "paid",
                true,
                "monthly"
        );
        command = new SubscriptionPurchaseCompletionCommand(
                "TRIBUTE",
                "shop_order:" + ORDER_ID,
                ORDER_ID.toString(),
                ORDER_ID.toString(),
                null,
                null,
                100_000L,
                "RUB",
                BillingPeriod.MONTH,
                null,
                PERIOD_START,
                PERIOD_END
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldProcessValidWebhookInExactOrder(
            boolean newlyProcessed
    ) {
        givenValidCommand();
        given(completionService.processCompletion(command))
                .willReturn(newlyProcessed);

        boolean result = service.processWebhook(
                RAW_BODY,
                SIGNATURE
        );

        assertEquals(newlyProcessed, result);

        InOrder order = inOrder(
                signatureVerifier,
                objectMapper,
                payloadDecoder,
                commandMapper,
                completionService
        );
        order.verify(signatureVerifier).isValid(
                RAW_BODY,
                SIGNATURE
        );
        order.verify(objectMapper).readValue(
                RAW_BODY,
                TributeWebhookEvent.class
        );
        order.verify(payloadDecoder).decodeShopOrder(event);
        order.verify(commandMapper).toPurchaseCompletionCommand(
                event,
                payload
        );
        order.verify(completionService).processCompletion(command);
        order.verifyNoMoreInteractions();
    }

    @Test
    void shouldRejectInvalidSignatureBeforeReadingBody() {
        given(signatureVerifier.isValid(
                RAW_BODY,
                SIGNATURE
        )).willReturn(false);

        InvalidTributeWebhookSignatureException exception =
                assertThrows(
                        InvalidTributeWebhookSignatureException.class,
                        () -> service.processWebhook(
                                RAW_BODY,
                                SIGNATURE
                        )
                );

        assertEquals(
                "Недействительная подпись webhook Tribute",
                exception.getMessage()
        );
        verify(signatureVerifier).isValid(RAW_BODY, SIGNATURE);
        verifyNoInteractions(
                objectMapper,
                payloadDecoder,
                commandMapper,
                completionService
        );
    }

    @Test
    void shouldMapJacksonEnvelopeFailureToSafeException() {
        JacksonException cause = mock(JacksonException.class);
        given(signatureVerifier.isValid(
                RAW_BODY,
                SIGNATURE
        )).willReturn(true);
        given(objectMapper.readValue(
                RAW_BODY,
                TributeWebhookEvent.class
        )).willThrow(cause);

        InvalidTributeWebhookException exception = assertThrows(
                InvalidTributeWebhookException.class,
                () -> service.processWebhook(
                        RAW_BODY,
                        SIGNATURE
                )
        );

        assertAll(
                () -> assertEquals(
                        "Некорректные данные webhook Tribute",
                        exception.getMessage()
                ),
                () -> assertSame(cause, exception.getCause())
        );
        verifyNoInteractions(
                payloadDecoder,
                commandMapper,
                completionService
        );
    }

    @Test
    void shouldMapDirectEnvelopeFailureToSafeException() {
        IllegalArgumentException cause = new IllegalArgumentException(
                "private@example.test synthetic-payment-token"
        );
        given(signatureVerifier.isValid(
                RAW_BODY,
                SIGNATURE
        )).willReturn(true);
        given(objectMapper.readValue(
                RAW_BODY,
                TributeWebhookEvent.class
        )).willThrow(cause);

        InvalidTributeWebhookException exception = assertThrows(
                InvalidTributeWebhookException.class,
                () -> service.processWebhook(
                        RAW_BODY,
                        SIGNATURE
                )
        );

        assertAll(
                () -> assertEquals(
                        "Некорректные данные webhook Tribute",
                        exception.getMessage()
                ),
                () -> assertSame(cause, exception.getCause())
        );
        verifyNoInteractions(
                payloadDecoder,
                commandMapper,
                completionService
        );
    }

    @Test
    void shouldPropagatePayloadDecoderFailureUnchanged() {
        InvalidTributeWebhookException failure =
                new InvalidTributeWebhookException();
        givenValidEnvelope();
        given(payloadDecoder.decodeShopOrder(event))
                .willThrow(failure);

        InvalidTributeWebhookException result = assertThrows(
                InvalidTributeWebhookException.class,
                () -> service.processWebhook(
                        RAW_BODY,
                        SIGNATURE
                )
        );

        assertSame(failure, result);
        verifyNoInteractions(commandMapper, completionService);
    }

    @Test
    void shouldPropagateCompletionFailureUnchanged() {
        SubscriptionPurchaseIntentNotFoundException failure =
                new SubscriptionPurchaseIntentNotFoundException();
        givenValidCommand();
        given(completionService.processCompletion(command))
                .willThrow(failure);

        SubscriptionPurchaseIntentNotFoundException result =
                assertThrows(
                        SubscriptionPurchaseIntentNotFoundException.class,
                        () -> service.processWebhook(
                                RAW_BODY,
                                SIGNATURE
                        )
                );

        assertSame(failure, result);
    }

    @Test
    void shouldRejectEveryMissingDependency() {
        assertAll(
                () -> assertConstructorFailure(
                        null,
                        objectMapper,
                        payloadDecoder,
                        commandMapper,
                        completionService,
                        "Verifier подписи Tribute не должен быть null"
                ),
                () -> assertConstructorFailure(
                        signatureVerifier,
                        null,
                        payloadDecoder,
                        commandMapper,
                        completionService,
                        "ObjectMapper не должен быть null"
                ),
                () -> assertConstructorFailure(
                        signatureVerifier,
                        objectMapper,
                        null,
                        commandMapper,
                        completionService,
                        "Decoder payload Tribute не должен быть null"
                ),
                () -> assertConstructorFailure(
                        signatureVerifier,
                        objectMapper,
                        payloadDecoder,
                        null,
                        completionService,
                        "Mapper команд Tribute не должен быть null"
                ),
                () -> assertConstructorFailure(
                        signatureVerifier,
                        objectMapper,
                        payloadDecoder,
                        commandMapper,
                        null,
                        "Сервис завершения покупки не должен быть null"
                )
        );
    }

    private void givenValidEnvelope() {
        given(signatureVerifier.isValid(
                RAW_BODY,
                SIGNATURE
        )).willReturn(true);
        given(objectMapper.readValue(
                RAW_BODY,
                TributeWebhookEvent.class
        )).willReturn(event);
    }

    private void givenValidCommand() {
        givenValidEnvelope();
        given(payloadDecoder.decodeShopOrder(event))
                .willReturn(payload);
        given(commandMapper.toPurchaseCompletionCommand(
                event,
                payload
        )).willReturn(command);
    }

    private void assertConstructorFailure(
            TributeWebhookSignatureVerifier verifier,
            ObjectMapper mapper,
            TributeWebhookPayloadDecoder decoder,
            TributeWebhookCommandMapper webhookCommandMapper,
            SubscriptionPurchaseCompletionService purchaseCompletionService,
            String expectedMessage
    ) {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new TributeWebhookService(
                        verifier,
                        mapper,
                        decoder,
                        webhookCommandMapper,
                        purchaseCompletionService,
                        lifecycleService,
                        purchaseFailureService,
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
                )
        );

        assertEquals(expectedMessage, exception.getMessage());
    }
}
