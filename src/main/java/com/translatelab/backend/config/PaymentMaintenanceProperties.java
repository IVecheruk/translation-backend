package com.translatelab.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.payment-maintenance")
public record PaymentMaintenanceProperties(
        @DefaultValue("100") @Min(1) @Max(1000) int batchSize,
        @DefaultValue("90d") @NotNull Duration terminalIntentRetention,
        @DefaultValue("400d") @NotNull Duration processedEventRetention
) {
    public PaymentMaintenanceProperties {
        requirePositive(terminalIntentRetention, "Хранение платёжных заявок");
        requirePositive(processedEventRetention, "Хранение платёжных событий");
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " должно быть положительным");
        }
    }
}
