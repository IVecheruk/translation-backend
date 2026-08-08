package com.translatelab.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
        Duration readTimeout,

        @DefaultValue("65536") int webhookMaxBodyBytes,

        @DefaultValue("120") int webhookRequestsPerMinute
) {

    private static final String EXPECTED_API_HOST = "tribute.tg";

    private static final Duration MAX_CONNECT_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration MAX_READ_TIMEOUT =
            Duration.ofSeconds(30);

    @ConstructorBinding
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
        if (webhookMaxBodyBytes < 1024 || webhookMaxBodyBytes > 65_536) {
            throw new IllegalArgumentException(
                    "Максимальный размер webhook Tribute должен быть "
                            + "от 1024 до 65536 байт"
            );
        }
        if (webhookRequestsPerMinute < 1 || webhookRequestsPerMinute > 10_000) {
            throw new IllegalArgumentException(
                    "Лимит webhook Tribute должен быть от 1 до 10000 запросов в минуту"
            );
        }
    }

    public TributeProperties(
            boolean enabled,
            URI baseUrl,
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        this(
                enabled,
                baseUrl,
                apiKey,
                connectTimeout,
                readTimeout,
                65_536,
                120
        );
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

        if (!EXPECTED_API_HOST.equalsIgnoreCase(baseUrl.getHost())
                || baseUrl.getPort() != -1
                || !("/api/v1".equals(baseUrl.getPath())
                || "/api/v1/".equals(baseUrl.getPath()))) {
            throw new IllegalArgumentException(
                    "Базовый адрес Tribute должен указывать на "
                            + "https://tribute.tg/api/v1"
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
                + ", webhookMaxBodyBytes=" + webhookMaxBodyBytes
                + ", webhookRequestsPerMinute=" + webhookRequestsPerMinute
                + ']';
    }

}
