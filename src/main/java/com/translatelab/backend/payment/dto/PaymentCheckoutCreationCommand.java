package com.translatelab.backend.payment.dto;

import com.translatelab.backend.payment.entity.BillingPeriod;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record PaymentCheckoutCreationCommand(
        UUID intentId,
        String offerCode,
        String planCode,
        String planDisplayName,
        long priceMinor,
        String currency,
        BillingPeriod billingPeriod,
        String externalProductId,
        Instant expiresAt
) {

    private static final Pattern OFFER_CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,63}$"
    );

    private static final Pattern PLAN_CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "^[A-Z]{3}$"
    );

    public PaymentCheckoutCreationCommand {
        validateIntentId(intentId);
        validateOfferCode(offerCode);
        validatePlanCode(planCode);
        planDisplayName = normalizePlanDisplayName(planDisplayName);
        validatePriceMinor(priceMinor);
        validateCurrency(currency);
        validateBillingPeriod(billingPeriod);
        externalProductId = normalizeExternalProductId(
                externalProductId
        );
        validateExpiresAt(expiresAt);
    }

    private static void validateIntentId(UUID intentId) {
        if (intentId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор заявки не должен быть null"
            );
        }
    }

    private static void validateOfferCode(String offerCode) {
        if (offerCode == null
                || !OFFER_CODE_PATTERN.matcher(offerCode).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного предложения"
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

    private static String normalizePlanDisplayName(
            String planDisplayName
    ) {
        if (planDisplayName == null) {
            throw new IllegalArgumentException(
                    "Название тарифа не должно быть null"
            );
        }

        String normalized = planDisplayName.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Название тарифа не должно быть пустым"
            );
        }

        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "Название тарифа не должно превышать 100 символов"
            );
        }

        return normalized;
    }

    private static void validatePriceMinor(long priceMinor) {
        if (priceMinor <= 0) {
            throw new IllegalArgumentException(
                    "Цена должна быть больше нуля"
            );
        }
    }

    private static void validateCurrency(String currency) {
        if (currency == null
                || !CURRENCY_PATTERN.matcher(currency).matches()) {
            throw new IllegalArgumentException(
                    "Валюта должна состоять из трёх заглавных латинских букв"
            );
        }
    }

    private static void validateBillingPeriod(
            BillingPeriod billingPeriod
    ) {
        if (billingPeriod == null) {
            throw new IllegalArgumentException(
                    "Период оплаты не должен быть null"
            );
        }
    }

    private static String normalizeExternalProductId(
            String externalProductId
    ) {
        if (externalProductId == null) {
            return null;
        }

        String normalized = externalProductId.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор продукта не должен быть пустым"
            );
        }

        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор продукта "
                            + "не должен превышать 255 символов"
            );
        }

        return normalized;
    }

    private static void validateExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Срок действия заявки не должен быть null"
            );
        }
    }
}
