package com.translatelab.backend.payment.dto;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record SubscriptionPurchaseIntentCreationResult(
        UUID intentId,
        String provider,
        Instant expiresAt
) {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    public SubscriptionPurchaseIntentCreationResult {
        validateIntentId(intentId);
        validateProvider(provider);
        validateExpiresAt(expiresAt);
    }

    private static void validateIntentId(UUID intentId) {
        if (intentId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор заявки не должен быть null"
            );
        }
    }

    private static void validateProvider(String provider) {
        if (provider == null
                || !PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного провайдера"
            );
        }
    }

    private static void validateExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Срок действия заявки не должен быть null"
            );
        }
    }

}