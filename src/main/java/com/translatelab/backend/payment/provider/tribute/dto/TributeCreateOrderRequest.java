package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.UUID;

public record TributeCreateOrderRequest(

        @JsonProperty("amount")
        long amount,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("title")
        String title,

        @JsonProperty("description")
        String description,

        @JsonProperty("customerId")
        UUID customerId,

        @JsonProperty("period")
        String period
) {

    private static final Set<String> SUPPORTED_CURRENCIES =
            Set.of("eur", "rub", "usd");

    private static final String MONTHLY_PERIOD = "monthly";

    public TributeCreateOrderRequest {
        validateAmount(amount);
        validateCurrency(currency);

        title = normalizeRequiredText(
                title,
                100,
                "Название заказа"
        );

        description = normalizeRequiredText(
                description,
                300,
                "Описание заказа"
        );

        validateCustomerId(customerId);
        validatePeriod(period);
    }

    private static void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Сумма заказа должна быть больше нуля"
            );
        }
    }

    private static void validateCurrency(String currency) {
        if (currency == null
                || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException(
                    "Tribute поддерживает валюты eur, rub и usd "
                            + "в нижнем регистре"
            );
        }
    }

    private static String normalizeRequiredText(
            String value,
            int maximumLength,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " не должно быть null"
            );
        }

        String normalized = value.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " не должно быть пустым"
            );
        }

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " не должно превышать "
                            + maximumLength + " символов"
            );
        }

        return normalized;
    }

    private static void validateCustomerId(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор клиента не должен быть null"
            );
        }
    }

    private static void validatePeriod(String period) {
        if (!MONTHLY_PERIOD.equals(period)) {
            throw new IllegalArgumentException(
                    "Период заказа Tribute должен быть monthly"
            );
        }
    }
}
