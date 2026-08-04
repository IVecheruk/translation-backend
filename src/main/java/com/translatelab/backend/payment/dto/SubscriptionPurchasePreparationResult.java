package com.translatelab.backend.payment.dto;

public record SubscriptionPurchasePreparationResult(
        SubscriptionPurchaseIntentCreationResult intentCreationResult,
        PaymentCheckoutCreationCommand checkoutCommand
) {

    public SubscriptionPurchasePreparationResult {
        validateIntentCreationResult(intentCreationResult);
        validateCheckoutCommand(checkoutCommand);
        validateMatchingIntentIds(
                intentCreationResult,
                checkoutCommand
        );
        validateMatchingExpirations(
                intentCreationResult,
                checkoutCommand
        );
    }

    private static void validateIntentCreationResult(
            SubscriptionPurchaseIntentCreationResult intentCreationResult
    ) {
        if (intentCreationResult == null) {
            throw new IllegalArgumentException(
                    "Результат создания заявки не должен быть null"
            );
        }
    }

    private static void validateCheckoutCommand(
            PaymentCheckoutCreationCommand checkoutCommand
    ) {
        if (checkoutCommand == null) {
            throw new IllegalArgumentException(
                    "Команда создания checkout не должна быть null"
            );
        }
    }

    private static void validateMatchingIntentIds(
            SubscriptionPurchaseIntentCreationResult intentCreationResult,
            PaymentCheckoutCreationCommand checkoutCommand
    ) {
        if (!intentCreationResult
                .intentId()
                .equals(checkoutCommand.intentId())) {
            throw new IllegalArgumentException(
                    "Идентификаторы подготовленной заявки должны совпадать"
            );
        }
    }

    private static void validateMatchingExpirations(
            SubscriptionPurchaseIntentCreationResult intentCreationResult,
            PaymentCheckoutCreationCommand checkoutCommand
    ) {
        if (!intentCreationResult
                .expiresAt()
                .equals(checkoutCommand.expiresAt())) {
            throw new IllegalArgumentException(
                    "Сроки действия подготовленной заявки должны совпадать"
            );
        }
    }
}
