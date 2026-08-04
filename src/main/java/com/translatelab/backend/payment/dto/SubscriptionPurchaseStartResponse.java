package com.translatelab.backend.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public record SubscriptionPurchaseStartResponse(

        @JsonProperty("intent_id")
        UUID intentId,

        String provider,

        @JsonProperty("redirect_url")
        URI redirectUrl,

        @JsonProperty("expires_at")
        Instant expiresAt
) {

    private static final Pattern PROVIDER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]{0,31}$"
    );

    private static final int MAX_REDIRECT_URL_LENGTH = 2048;

    public SubscriptionPurchaseStartResponse {
        validateIntentId(intentId);
        validateProvider(provider);
        validateRedirectUrl(redirectUrl);
        validateExpiresAt(expiresAt);
    }

    private static void validateIntentId(UUID intentId) {
        if (intentId == null) {
            throw new IllegalArgumentException(
                    "Идентификатор заявки не должен быть null"
            );
        }
    }

    private static void validateProvider(String provider) {
        if (provider == null
                || !PROVIDER_PATTERN.matcher(provider).matches()) {
            throw new IllegalArgumentException(
                    "Некорректный формат кода платёжного провайдера"
            );
        }
    }

    private static void validateRedirectUrl(URI redirectUrl) {
        if (redirectUrl == null) {
            throw new IllegalArgumentException(
                    "Ссылка перенаправления не должна быть null"
            );
        }

        if (!redirectUrl.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Ссылка перенаправления должна быть абсолютной"
            );
        }

        if (!"https".equalsIgnoreCase(redirectUrl.getScheme())) {
            throw new IllegalArgumentException(
                    "Ссылка перенаправления должна использовать HTTPS"
            );
        }

        if (redirectUrl.getHost() == null
                || redirectUrl.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "Ссылка перенаправления должна содержать хост"
            );
        }

        if (redirectUrl.toString().length()
                > MAX_REDIRECT_URL_LENGTH) {
            throw new IllegalArgumentException(
                    "Ссылка перенаправления "
                            + "не должна превышать 2048 символов"
            );
        }
    }

    private static void validateExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Срок действия заявки не должен быть null"
            );
        }
    }
}
