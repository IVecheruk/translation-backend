package com.translatelab.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.payment")
@Validated
public record PaymentProperties(

        @NotNull
        Duration purchaseIntentTtl
) {

    public PaymentProperties {
        if (purchaseIntentTtl != null
                && (purchaseIntentTtl.isZero()
                || purchaseIntentTtl.isNegative())) {
            throw new IllegalArgumentException(
                    "Срок действия заявки на покупку должен быть положительным"
            );
        }
    }
}