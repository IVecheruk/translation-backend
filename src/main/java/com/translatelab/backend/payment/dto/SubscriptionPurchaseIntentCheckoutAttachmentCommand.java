package com.translatelab.backend.payment.dto;

import java.util.UUID;

public record SubscriptionPurchaseIntentCheckoutAttachmentCommand(
        UUID userId,
        UUID intentId,
        String externalCheckoutId
) {

    public SubscriptionPurchaseIntentCheckoutAttachmentCommand {
        validateUserId(userId);
        validateIntentId(intentId);

        externalCheckoutId = normalizeExternalCheckoutId(
                externalCheckoutId
        );
    }

    private static void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор пользователя не должен быть null"
            );
        }
    }

    private static void validateIntentId(UUID intentId) {
        if (intentId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор заявки не должен быть null"
            );
        }
    }

    private static String normalizeExternalCheckoutId(
            String externalCheckoutId
    ) {
        if (externalCheckoutId == null) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор checkout не должен быть null"
            );
        }

        String normalized = externalCheckoutId.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор checkout не должен быть пустым"
            );
        }

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор checkout "
                            + "не должен превышать 255 символов"
            );
        }

        return normalized;
    }
}