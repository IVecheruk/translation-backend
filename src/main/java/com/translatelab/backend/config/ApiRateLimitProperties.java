package com.translatelab.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record ApiRateLimitProperties(
        @DefaultValue("30") @Min(1) @Max(10_000)
        int registrationPerMinute,
        @DefaultValue("20") @Min(1) @Max(10_000)
        int loginPerMinute,
        @DefaultValue("30") @Min(1) @Max(10_000)
        int documentUploadPerMinute,
        @DefaultValue("10") @Min(1) @Max(10_000)
        int checkoutPerMinute,
        @DefaultValue("5") @Min(1) @Max(10_000)
        int accountEmailRequestPerMinute,
        @DefaultValue("20") @Min(1) @Max(10_000)
        int accountTokenConfirmationPerMinute,
        @DefaultValue("50000") @Min(100) @Max(1_000_000)
        int maximumTrackedKeys
) {}
