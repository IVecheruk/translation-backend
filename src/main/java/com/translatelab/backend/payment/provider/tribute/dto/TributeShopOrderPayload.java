package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
import java.util.UUID;


@JsonIgnoreProperties(ignoreUnknown = true)
public record TributeShopOrderPayload(

        UUID uuid,

        long amount,

        String currency,

        String status,

        @JsonProperty("isRecurrent")
        boolean recurrent,

        String period
) {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("eur", "rub", "usd");
    private static final String PAID_STATUS = "paid";
    private static final String MONTHLY_PERIOD = "monthly";

    public TributeShopOrderPayload {
        validateUuid(uuid);
        validateAmount(amount);
        validateCurrency(currency);
        validateStatus(status);
        validatePeriod(period);
    }

    private static void validateUuid(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Идентификатор заказа Tribute не должен быть null");
        }
    }

    private static void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма заказа Tribute должна быть больше нуля");
        }
    }

    private static void validateCurrency(String currency) {
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("Валюта заказа Tribute не поддерживается");
        }
    }

    private static void validateStatus(String status) {
        if (!PAID_STATUS.equals(status)) {
            throw new IllegalArgumentException("Заказ Tribute не находиться в оплаченном статусе");
        }
    }

    private static void validatePeriod(String period) {
        if (!MONTHLY_PERIOD.equals(period)) {
            throw new IllegalArgumentException("Заказ Tribute должен иметь месячный период");
        }
    }

}
