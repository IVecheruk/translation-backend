package com.translatelab.backend.payment.dto;

import java.time.Instant;
import java.util.regex.Pattern;

public record SubscriptionPurchaseCompletionCommand(
        String provider,
        String externalEventId,
        String externalCheckoutId,
        String externalCustomerId,
        String externalSubscriptionId,
        Instant periodStart,
        Instant periodEnd
) {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    public SubscriptionPurchaseCompletionCommand {
        validateProvider(provider);

        externalEventId = normalizeRequiredExternalId(
                externalEventId,
                "Внешний идентификатор события"
        );
        externalCheckoutId = normalizeRequiredExternalId(
                externalCheckoutId,
                "Внешний идентификатор checkout"
        );
        externalCustomerId = normalizeOptionalExternalId(
                externalCustomerId,
                "Внешний идентификатор клиента"
        );
        externalSubscriptionId = normalizeRequiredExternalId(
                externalSubscriptionId,
                "Внешний идентификатор подписки"
        );

        validatePeriod(periodStart, periodEnd);
    }

    private static void validateProvider(String provider) {
        if (provider == null
                || !PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного провайдера"
            );
        }
    }

    private static String normalizeRequiredExternalId(
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

    private static String normalizeOptionalExternalId(
            String value,
            String fieldName
    ) {
        if (value == null) {
            return null;
        }

        return normalizeRequiredExternalId(value, fieldName);
    }

    private static void validatePeriod(
            Instant periodStart,
            Instant periodEnd
    ) {
        if (periodStart == null) {
            throw new IllegalArgumentException(
                    "Начало оплаченного периода не должно быть null"
            );
        }

        if (periodEnd == null) {
            throw new IllegalArgumentException(
                    "Конец оплаченного периода не должен быть null"
            );
        }

        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException(
                    "Конец оплаченного периода должен быть позже его начала"
            );
        }
    }
}