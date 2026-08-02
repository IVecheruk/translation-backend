package com.translatelab.backend.payment.dto;

import java.util.UUID;
import java.util.regex.Pattern;

public record SubscriptionPurchaseIntentCreationCommand(
        UUID userId,
        String planCode,
        String provider
) {

    private static final Pattern PLAN_CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    public SubscriptionPurchaseIntentCreationCommand {
        validateUserId(userId);
        validatePlanCode(planCode);
        validateProvider(provider);
    }

    private static void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор пользователя не должен быть null"
            );
        }
    }

    private static void validatePlanCode(String planCode) {
        if (planCode == null
                || !PLAN_CODE_PATTERN.matcher(planCode).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода тарифа"
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
}