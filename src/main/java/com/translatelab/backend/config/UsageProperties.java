package com.translatelab.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
@ConfigurationProperties(prefix = "app.usage")
@Validated
public record UsageProperties(

        @NotNull
        Duration reservationTtl
) {

    public UsageProperties {
        if (reservationTtl != null && (reservationTtl.isZero() || reservationTtl.isNegative())) {
            throw new IllegalArgumentException("Срок действия резервации должен быть положительным");
        }
    }
}
