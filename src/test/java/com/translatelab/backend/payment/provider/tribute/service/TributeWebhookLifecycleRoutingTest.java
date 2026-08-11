package com.translatelab.backend.payment.provider.tribute.service;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookCommandMapper;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookPayloadDecoder;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookSignatureVerifier;
import com.translatelab.backend.payment.provider.tribute.dto.TributeFailedOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeRecurringOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
import com.translatelab.backend.payment.service.SubscriptionProviderLifecycleService;
import com.translatelab.backend.payment.service.SubscriptionPurchaseCompletionService;
import com.translatelab.backend.payment.service.SubscriptionPurchaseFailureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TributeWebhookLifecycleRoutingTest {

    private static final byte[] BODY = "{}".getBytes();
    private static final String SIGNATURE = "a".repeat(64);
    private static final Instant CREATED = Instant.parse("2026-08-08T12:00:00Z");
    private static final UUID ORDER = UUID.randomUUID();

    @Mock private TributeWebhookSignatureVerifier signatureVerifier;
    @Mock private ObjectMapper objectMapper;
    @Mock private TributeWebhookPayloadDecoder decoder;
    @Mock private TributeWebhookCommandMapper commandMapper;
    @Mock private SubscriptionPurchaseCompletionService completionService;
    @Mock private SubscriptionProviderLifecycleService lifecycleService;
    @Mock private SubscriptionPurchaseFailureService purchaseFailureService;

    private TributeWebhookService service;

    @BeforeEach
    void setUp() {
        service = new TributeWebhookService(
                signatureVerifier,
                objectMapper,
                decoder,
                commandMapper,
                completionService,
                lifecycleService,
                purchaseFailureService,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
        given(signatureVerifier.isValid(BODY, SIGNATURE)).willReturn(true);
    }

    @Test
    void shouldRouteRecurringSuccess() throws Exception {
        TributeWebhookEvent event = event("shop_order_charge_success");
        TributeRecurringOrderPayload payload = recurringPayload();
        givenEnvelope(event);
        given(decoder.decodeRecurringOrder(event)).willReturn(payload);
        given(lifecycleService.processRecurringCharge(
                "TRIBUTE", eventId(event), ORDER.toString(),
                49_900, "RUB", BillingPeriod.MONTH
        )).willReturn(true);

        assertTrue(service.processWebhook(BODY, SIGNATURE));

        verify(lifecycleService).processRecurringCharge(
                "TRIBUTE", eventId(event), ORDER.toString(),
                49_900, "RUB", BillingPeriod.MONTH
        );
    }

    @Test
    void shouldRouteRecurringFailure() throws Exception {
        TributeWebhookEvent event = event("shop_order_charge_failed");
        TributeRecurringOrderPayload payload = recurringPayload();
        givenEnvelope(event);
        given(decoder.decodeRecurringOrder(event)).willReturn(payload);
        given(lifecycleService.processChargeFailure(
                "TRIBUTE", eventId(event), ORDER.toString(),
                49_900, "RUB", BillingPeriod.MONTH
        )).willReturn(true);

        assertTrue(service.processWebhook(BODY, SIGNATURE));
    }

    @Test
    void shouldRouteCancellation() throws Exception {
        TributeWebhookEvent event = event("shop_order_cancelled");
        givenEnvelope(event);
        given(decoder.decodeRecurringOrder(event)).willReturn(recurringPayload());
        given(lifecycleService.processCancellation(
                "TRIBUTE", eventId(event), ORDER.toString()
        )).willReturn(true);

        assertTrue(service.processWebhook(BODY, SIGNATURE));
    }

    @Test
    void shouldRouteRefundAsRevocation() throws Exception {
        TributeWebhookEvent event = event("shop_order_refunded");
        TributeFailedOrderPayload payload = failedPayload();
        givenEnvelope(event);
        given(decoder.decodeFailedOrder(event)).willReturn(payload);
        given(lifecycleService.processRevocation(
                "TRIBUTE", eventId(event), ORDER.toString(), 49_900, "RUB"
        )).willReturn(true);

        assertTrue(service.processWebhook(BODY, SIGNATURE));
    }

    @Test
    void shouldRouteInitialPaymentFailureToIntent() throws Exception {
        TributeWebhookEvent event = event("shop_order_payment_failed");
        TributeFailedOrderPayload payload = failedPayload();
        givenEnvelope(event);
        given(decoder.decodeFailedOrder(event)).willReturn(payload);
        given(purchaseFailureService.processFailure(
                "TRIBUTE", eventId(event), ORDER.toString(), 49_900, "RUB"
        )).willReturn(true);

        assertTrue(service.processWebhook(BODY, SIGNATURE));
    }

    private void givenEnvelope(TributeWebhookEvent event) throws Exception {
        given(objectMapper.readValue(BODY, TributeWebhookEvent.class))
                .willReturn(event);
    }

    private TributeWebhookEvent event(String name) {
        return new TributeWebhookEvent(
                name,
                CREATED,
                CREATED.plusMillis(1),
                JsonMapper.builder().build().createObjectNode()
        );
    }

    private TributeRecurringOrderPayload recurringPayload() {
        return new TributeRecurringOrderPayload(
                ORDER, 49_900, "rub", "monthly"
        );
    }

    private TributeFailedOrderPayload failedPayload() {
        return new TributeFailedOrderPayload(ORDER, 49_900, "rub");
    }

    private String eventId(TributeWebhookEvent event) {
        return event.name() + ':' + ORDER + ':' + CREATED;
    }
}
