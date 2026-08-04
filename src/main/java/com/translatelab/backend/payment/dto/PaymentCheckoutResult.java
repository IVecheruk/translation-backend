package com.translatelab.backend.payment.dto;

import java.net.URI;

public record PaymentCheckoutResult(
        String externalCheckoutId,
        URI redirectUrl
) {

    private static final int MAX_CHECKOUT_ID_LENGTH = 255;
    private static final int MAX_REDIRECT_URL_LENGTH = 2048;

    public PaymentCheckoutResult {
        externalCheckoutId = normalizeExternalCheckoutId(
                externalCheckoutId
        );
        validateRedirectUrl(redirectUrl);
    }

    private static String normalizeExternalCheckoutId(
            String externalCheckoutId
    ) {
        if (externalCheckoutId == null) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор checkout не должен быть null"
            );
        }

        String normalized = externalCheckoutId.strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор checkout не должен быть пустым"
            );
        }

        if (normalized.length() > MAX_CHECKOUT_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Внешний идентификатор checkout "
                            + "не должен превышать 255 символов"
            );
        }

        return normalized;
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
}
