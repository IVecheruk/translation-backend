package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.provider.tribute.dto.TributeCreateOrderRequest;
import com.translatelab.backend.payment.provider.tribute.dto.TributeCreateOrderResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class TributeCheckoutMapper {

    public TributeCreateOrderRequest toCreateOrderRequest(
            PaymentCheckoutCreationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда создания checkout не должна быть null"
        );

        return new TributeCreateOrderRequest(
                command.priceMinor(),
                mapCurrency(command.currency()),
                command.planDisplayName(),
                buildDescription(command.planDisplayName()),
                command.intentId(),
                mapBillingPeriod(command.billingPeriod())
        );
    }

    public PaymentCheckoutResult toCheckoutResult(
            TributeCreateOrderResponse response
    ) {
        Objects.requireNonNull(
                response,
                "Ответ Tribute не должен быть null"
        );

        return new PaymentCheckoutResult(
                response.uuid().toString(),
                response.webappPaymentUrl()
        );
    }

    private String mapCurrency(String currency) {
        return switch (currency) {
            case "EUR" -> "eur";
            case "RUB" -> "rub";
            case "USD" -> "usd";
            default -> throw new IllegalArgumentException(
                    "Валюта платёжного предложения "
                            + "не поддерживается Tribute"
            );
        };
    }

    private String mapBillingPeriod(
            BillingPeriod billingPeriod
    ) {
        return switch (billingPeriod) {
            case MONTH -> "monthly";
        };
    }

    private String buildDescription(String planDisplayName) {
        return "Подписка «"
                + planDisplayName
                + "» на один месяц";
    }
}
