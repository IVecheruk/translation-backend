package com.translatelab.backend.payment.provider.tribute.service;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookCommandMapper;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookPayloadDecoder;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookSignatureVerifier;
import com.translatelab.backend.payment.provider.tribute.dto.TributeShopOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
import com.translatelab.backend.payment.provider.tribute.dto.TributeRecurringOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeFailedOrderPayload;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.service.SubscriptionProviderLifecycleService;
import com.translatelab.backend.payment.service.SubscriptionPurchaseFailureService;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookSignatureException;
import com.translatelab.backend.payment.service.SubscriptionPurchaseCompletionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Service
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributeWebhookService {

    private final TributeWebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final TributeWebhookPayloadDecoder payloadDecoder;
    private final TributeWebhookCommandMapper commandMapper;
    private final SubscriptionPurchaseCompletionService completionService;
    private final SubscriptionProviderLifecycleService lifecycleService;
    private final SubscriptionPurchaseFailureService purchaseFailureService;

    public TributeWebhookService(
            TributeWebhookSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            TributeWebhookPayloadDecoder payloadDecoder,
            TributeWebhookCommandMapper commandMapper,
            SubscriptionPurchaseCompletionService completionService,
            SubscriptionProviderLifecycleService lifecycleService,
            SubscriptionPurchaseFailureService purchaseFailureService
    ) {
        this.signatureVerifier = Objects.requireNonNull(
                signatureVerifier,
                "Verifier подписи Tribute не должен быть null"
        );

        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "ObjectMapper не должен быть null"
        );

        this.payloadDecoder = Objects.requireNonNull(
                payloadDecoder,
                "Decoder payload Tribute не должен быть null"
        );

        this.commandMapper = Objects.requireNonNull(
                commandMapper,
                "Mapper команд Tribute не должен быть null"
        );

        this.completionService = Objects.requireNonNull(
                completionService,
                "Сервис завершения покупки не должен быть null"
        );
        this.lifecycleService = Objects.requireNonNull(
                lifecycleService,
                "Сервис lifecycle подписки не должен быть null"
        );
        this.purchaseFailureService = Objects.requireNonNull(
                purchaseFailureService,
                "Сервис неуспешной покупки не должен быть null"
        );
    }

    public boolean processWebhook(
            byte[] rawBody,
            String signature
    ) {
        if (!signatureVerifier.isValid(
                rawBody,
                signature
        )) {
            throw new InvalidTributeWebhookSignatureException();
        }

        TributeWebhookEvent event =
                deserializeEvent(rawBody);

        return switch (event.name()) {
            case "shop_order" -> processInitialPayment(event);
            case "shop_order_charge_success" -> processChargeSuccess(event);
            case "shop_order_charge_failed" -> processChargeFailure(event);
            case "shop_order_cancelled" -> processCancellation(event);
            case "shop_order_refunded" -> processRefund(event);
            case "shop_order_payment_failed" -> processInitialFailure(event);
            default -> throw new InvalidTributeWebhookException();
        };
    }

    private TributeWebhookEvent deserializeEvent(
            byte[] rawBody
    ) {
        try {
            return objectMapper.readValue(
                    rawBody,
                    TributeWebhookEvent.class
            );
        } catch (
                JacksonException
                | IllegalArgumentException exception
        ) {
            throw new InvalidTributeWebhookException(
                    exception
            );
        }
    }

    private boolean processInitialPayment(TributeWebhookEvent event) {
        TributeShopOrderPayload payload = payloadDecoder.decodeShopOrder(event);
        SubscriptionPurchaseCompletionCommand command =
                commandMapper.toPurchaseCompletionCommand(event, payload);
        return completionService.processCompletion(command);
    }

    private boolean processChargeSuccess(TributeWebhookEvent event) {
        TributeRecurringOrderPayload payload =
                payloadDecoder.decodeRecurringOrder(event);
        return lifecycleService.processRecurringCharge(
                "TRIBUTE", eventId(event, payload.uuid().toString()),
                payload.uuid().toString(), payload.amount(),
                upper(payload.currency()), BillingPeriod.MONTH
        );
    }

    private boolean processChargeFailure(TributeWebhookEvent event) {
        TributeRecurringOrderPayload payload =
                payloadDecoder.decodeRecurringOrder(event);
        return lifecycleService.processChargeFailure(
                "TRIBUTE", eventId(event, payload.uuid().toString()),
                payload.uuid().toString(), payload.amount(),
                upper(payload.currency()), BillingPeriod.MONTH
        );
    }

    private boolean processCancellation(TributeWebhookEvent event) {
        TributeRecurringOrderPayload payload =
                payloadDecoder.decodeRecurringOrder(event);
        return lifecycleService.processCancellation(
                "TRIBUTE", eventId(event, payload.uuid().toString()),
                payload.uuid().toString()
        );
    }

    private boolean processRefund(TributeWebhookEvent event) {
        TributeFailedOrderPayload payload = payloadDecoder.decodeFailedOrder(event);
        return lifecycleService.processRevocation(
                "TRIBUTE", eventId(event, payload.uuid().toString()),
                payload.uuid().toString(), payload.amount(), upper(payload.currency())
        );
    }

    private boolean processInitialFailure(TributeWebhookEvent event) {
        TributeFailedOrderPayload payload = payloadDecoder.decodeFailedOrder(event);
        return purchaseFailureService.processFailure(
                "TRIBUTE", eventId(event, payload.uuid().toString()),
                payload.uuid().toString(), payload.amount(), upper(payload.currency())
        );
    }

    private String eventId(TributeWebhookEvent event, String orderId) {
        return event.name() + ':' + orderId + ':' + event.createdAt();
    }

    private String upper(String value) {
        return value.toUpperCase(java.util.Locale.ROOT);
    }
}
