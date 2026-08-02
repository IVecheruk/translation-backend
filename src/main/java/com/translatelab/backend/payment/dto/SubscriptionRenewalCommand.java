package com.translatelab.backend.payment.dto;

import java.time.Instant;
import java.util.regex.Pattern;

public record SubscriptionRenewalCommand(
        String provider,
        String externalEventId,
        String externalSubscriptionId,
        Instant newPeriodStart,
        Instant newPeriodEnd
) {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    public SubscriptionRenewalCommand {
        validateProvider(provider);

        externalEventId = normalizeExternalId(
                externalEventId,
                "Внешний идентификатор события"
        );

        externalSubscriptionId = normalizeExternalId(
                externalSubscriptionId,
                "Внешний идентификатор подписки"
        );

        if (newPeriodStart == null) {
            throw new IllegalArgumentException(
                    "Начало нового периода не должно быть null"
            );
        }

        if (newPeriodEnd == null) {
            throw new IllegalArgumentException(
                    "Конец нового периода не должен быть null"
            );
        }

        if (!newPeriodEnd.isAfter(newPeriodStart)) {
            throw new IllegalArgumentException(
                    "Конец нового периода должен быть позже его начала"
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

    private static String normalizeExternalId(
            String value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " не должен быть null"
            );
        }

        String normalized = value.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " не должен быть пустым"
            );
        }

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    fieldName + " не должен превышать 255 символов"
            );
        }

        return normalized;
    }
}