package com.translatelab.backend.payment.dto;

import com.translatelab.backend.payment.entity.BillingPeriod;

import java.time.Instant;
import java.util.regex.Pattern;

public record SubscriptionPurchaseCompletionCommand(
        String provider,
        String externalEventId,
        String externalCheckoutId,
        String externalOrderId,
        String externalCustomerId,
        String externalSubscriptionId,
        long paidAmountMinor,
        String paidCurrency,
        BillingPeriod paidBillingPeriod,
        String paidExternalProductId,
        Instant periodStart,
        Instant periodEnd
) {

    private static final Pattern PROVIDER_PATTERN =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");
    private static final Pattern CURRENCY_PATTERN =
            Pattern.compile("^[A-Z]{3}$");

    public SubscriptionPurchaseCompletionCommand {
        if (provider == null || !PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного провайдера"
            );
        }
        externalEventId = normalizeRequired(externalEventId, "события");
        externalCheckoutId = normalizeRequired(externalCheckoutId, "checkout");
        externalOrderId = normalizeRequired(externalOrderId, "заказа");
        externalCustomerId = normalizeOptional(externalCustomerId, "клиента");
        externalSubscriptionId = normalizeOptional(
                externalSubscriptionId,
                "подписки"
        );
        if (paidAmountMinor <= 0) {
            throw new IllegalArgumentException("Сумма платежа должна быть больше нуля");
        }
        if (paidCurrency == null
                || !CURRENCY_PATTERN.matcher(paidCurrency).matches()) {
            throw new IllegalArgumentException("Некорректная валюта платежа");
        }
        if (paidBillingPeriod == null) {
            throw new IllegalArgumentException("Период платежа не должен быть null");
        }
        paidExternalProductId = normalizeOptional(
                paidExternalProductId,
                "продукта"
        );
        if (periodStart == null || periodEnd == null
                || !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("Некорректный оплаченный период");
        }
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = normalizeOptional(value, field);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор " + field + " не должен быть null"
            );
        }
        return normalized;
    }

    private static String normalizeOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "Некорректный внешний идентификатор " + field
            );
        }
        return normalized;
    }
}
