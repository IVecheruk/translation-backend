package com.translatelab.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.payment.tribute")
@Validated
public record TributeProperties(

        boolean enabled,

        @NotNull
        URI baseUrl,

        String apiKey,

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout
) {

    private static final Duration MAX_CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration MAX_READ_TIMEOUT =
            Duration.ofSeconds(30);

    public TributeProperties {
        validateBaseUrl(baseUrl);
        validateTimeout(
                connectTimeout,
                MAX_CONNECT_TIMEOUT,
                "Таймаут подключения к Tribute"
        );
        validateTimeout(
                readTimeout,
                MAX_READ_TIMEOUT,
                "Таймаут ответа Tribute"
        );
        validateApiKey(enabled, apiKey);
    }

    private static void validateBaseUrl(URI baseUrl) {
        if (baseUrl == null) {
            return;
        }

        if (!baseUrl.isAbsolute()
                || !"https".equalsIgnoreCase(baseUrl.getScheme())
                || baseUrl.getHost() == null
                || baseUrl.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "Базовый адрес Tribute должен быть абсолютным HTTPS URL"
            );
        }

        if (baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Базовый адрес Tribute не должен содержать "
                            + "учётные данные, параметры или фрагмент"
            );
        }
    }

    private static void validateTimeout(
            Duration timeout,
            Duration maximum,
            String propertyName
    ) {
        if (timeout == null) {
            return;
        }

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    propertyName + " должен быть положительным"
            );
        }

        if (timeout.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    propertyName + " не должен превышать " + maximum
            );
        }
    }

    private static void validateApiKey(
            boolean enabled,
            String apiKey
    ) {
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException(
                    "Для включения Tribute необходимо настроить API-ключ"
            );
        }
    }

    @Override
    public String toString() {
        return "TributeProperties["
                + "enabled=" + enabled
                + ", baseUrl=" + baseUrl
                + ", apiKey=<redacted>"
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ']';
    }

}
