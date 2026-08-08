package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.PaymentCheckoutResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCheckoutAttachmentCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchasePreparationResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseStartResponse;
import com.translatelab.backend.payment.provider.PaymentCheckoutGateway;
import com.translatelab.backend.payment.provider.PaymentCheckoutGatewayResolver;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SubscriptionPurchaseCheckoutService {

    private final SubscriptionPurchaseIntentCreationService creationService;
    private final PaymentCheckoutGatewayResolver gatewayResolver;
    private final SubscriptionPurchaseIntentCheckoutAttachmentService
            attachmentService;

    public SubscriptionPurchaseCheckoutService(
            SubscriptionPurchaseIntentCreationService creationService,
            PaymentCheckoutGatewayResolver gatewayResolver,
            SubscriptionPurchaseIntentCheckoutAttachmentService
                    attachmentService
    ) {
        this.creationService = creationService;
        this.gatewayResolver = gatewayResolver;
        this.attachmentService = attachmentService;
    }

    public SubscriptionPurchaseStartResponse start(
            SubscriptionPurchaseIntentCreationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда начала покупки не должна быть null"
        );

        SubscriptionPurchasePreparationResult preparation =
                creationService.prepare(command);

        SubscriptionPurchaseIntentCreationResult intentResult =
                preparation.intentCreationResult();

        PaymentCheckoutGateway gateway = gatewayResolver.resolve(
                intentResult.provider()
        );

        PaymentCheckoutResult checkoutResult = Objects.requireNonNull(
                gateway.createCheckout(preparation.checkoutCommand()),
                "Платёжный gateway не должен возвращать null"
        );

        try {
            attachmentService.attach(
                    new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                            command.userId(),
                            intentResult.intentId(),
                            checkoutResult.externalCheckoutId()
                    )
            );
        } catch (RuntimeException attachmentFailure) {
            try {
                gateway.cancelCheckout(checkoutResult.externalCheckoutId());
            } catch (RuntimeException compensationFailure) {
                attachmentFailure.addSuppressed(compensationFailure);
            }
            try {
                attachmentService.abandon(
                        command.userId(),
                        intentResult.intentId()
                );
            } catch (RuntimeException abandonmentFailure) {
                attachmentFailure.addSuppressed(abandonmentFailure);
            }
            throw attachmentFailure;
        }

        return new SubscriptionPurchaseStartResponse(
                intentResult.intentId(),
                intentResult.provider(),
                checkoutResult.redirectUrl(),
                intentResult.expiresAt()
        );
    }
}
