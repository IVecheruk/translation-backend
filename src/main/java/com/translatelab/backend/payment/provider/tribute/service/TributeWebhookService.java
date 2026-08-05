package com.translatelab.backend.payment.provider.tribute.service;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookCommandMapper;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookPayloadDecoder;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookSignatureVerifier;
import com.translatelab.backend.payment.provider.tribute.dto.TributeShopOrderPayload;
import com.translatelab.backend.payment.provider.tribute.dto.TributeWebhookEvent;
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

    public TributeWebhookService(
            TributeWebhookSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            TributeWebhookPayloadDecoder payloadDecoder,
            TributeWebhookCommandMapper commandMapper,
            SubscriptionPurchaseCompletionService completionService
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

        TributeShopOrderPayload payload =
                payloadDecoder.decodeShopOrder(event);

        SubscriptionPurchaseCompletionCommand command =
                commandMapper.toPurchaseCompletionCommand(
                        event,
                        payload
                );

        return completionService.processCompletion(command);
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
}
