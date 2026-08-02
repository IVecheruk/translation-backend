package com.translatelab.backend.payment.dto;

import java.util.regex.Pattern;

public record SubscriptionCancellationScheduleCommand(
        String provider,
        String externalEventId,
        String externalSubscriptionId
) {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    public SubscriptionCancellationScheduleCommand {
        validateProvider(provider);

        externalEventId = normalizeExternalId(
                externalEventId,
                "Внешний идентификатор события"
        );

        externalSubscriptionId = normalizeExternalId(
                externalSubscriptionId,
                "Внешний идентификатор подписки"
        );
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