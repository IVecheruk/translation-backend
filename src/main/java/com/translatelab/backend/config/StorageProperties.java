package com.translatelab.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@ConfigurationProperties(prefix = "app.storage")
@Validated
public record StorageProperties(

        @NotNull
        URI endpoint,

        @NotBlank
        String accessKey,

        @NotBlank
        String secretKey,

        @NotBlank
        @Size(min = 3, max = 63)
        @Pattern(
                regexp = "^[a-z0-9][a-z0-9.-]*[a-z0-9]$",
                message = "Имя bucket должно содержать только строчные латинские буквы, цифры, точки и дефисы"
        )
        String bucket
) {
}