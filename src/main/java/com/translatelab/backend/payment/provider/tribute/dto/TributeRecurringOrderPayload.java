package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TributeRecurringOrderPayload(
        UUID uuid,
        long amount,
        String currency,
        String period
) {
    private static final Set<String> CURRENCIES = Set.of("eur", "rub", "usd");

    public TributeRecurringOrderPayload {
        if (uuid == null || amount <= 0
                || !CURRENCIES.contains(currency)
                || !"monthly".equals(period)) {
            throw new IllegalArgumentException(
                    "Некорректные данные рекуррентного заказа Tribute"
            );
        }
    }
}
