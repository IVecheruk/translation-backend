package com.translatelab.backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.messaging")
@Validated
public record MessagingProperties(

        @NotBlank
        String exchange,

        @NotBlank
        String queue,

        @NotBlank
        String routingKey
) {
}