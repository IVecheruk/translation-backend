package com.translatelab.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.usage")
@Validated
public record UsageProperties(

        @NotNull
        Duration reservationTtl,

        @NotNull
        Duration cleanupInterval,

        @Min(1)
        int cleanupBatchSize
) {

    public UsageProperties {
        if (reservationTtl != null && (reservationTtl.isZero() || reservationTtl.isNegative())) {
            throw new IllegalArgumentException("Срок действия резервации должен быть положительным");
        }

        if (cleanupInterval != null && (cleanupInterval.isZero() || cleanupInterval.isNegative())) {
            throw new IllegalArgumentException("Интервал очистки должен быть положительным");
        }

        if (cleanupBatchSize <= 0) {
            throw new IllegalArgumentException("Размер пакета очистки должен быть положительным");
        }
    }
}