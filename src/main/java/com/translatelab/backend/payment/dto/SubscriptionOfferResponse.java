package com.translatelab.backend.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.translatelab.backend.payment.entity.BillingPeriod;

import java.util.regex.Pattern;

public record SubscriptionOfferResponse(

        @JsonProperty("plan_code")
        String planCode,

        @JsonProperty("plan_display_name")
        String planDisplayName,

        @JsonProperty("price_minor")
        long priceMinor,

        String currency,

        @JsonProperty("billing_period")
        BillingPeriod billingPeriod
) {

    private static final Pattern PLAN_CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "^[A-Z]{3}$"
    );

    public SubscriptionOfferResponse {
        validatePlanCode(planCode);
        planDisplayName = normalizePlanDisplayName(planDisplayName);
        validatePriceMinor(priceMinor);
        validateCurrency(currency);
        validateBillingPeriod(billingPeriod);
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
}