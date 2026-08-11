package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TributeFailedOrderPayload(
        UUID uuid,
        long amount,
        String currency
) {
    private static final Set<String> CURRENCIES = Set.of("eur", "rub", "usd");

    public TributeFailedOrderPayload {
        if (uuid == null || amount <= 0 || !CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException(
                    "Некорректные данные неуспешного заказа Tribute"
            );
        }
    }
}
