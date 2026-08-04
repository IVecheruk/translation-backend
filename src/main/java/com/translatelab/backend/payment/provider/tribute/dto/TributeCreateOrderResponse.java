package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TributeCreateOrderResponse(

        @JsonProperty("uuid")
        UUID uuid,

        @JsonProperty("paymentUrl")
        URI paymentUrl,

        @JsonProperty("webappPaymentUrl")
        URI webappPaymentUrl
) {

    private static final int MAX_URL_LENGTH = 2048;

    private static final String PAYMENT_HOST = "web.tribute.tg";
    private static final String TELEGRAM_HOST = "t.me";

    public TributeCreateOrderResponse {
        validateUuid(uuid);

        validateTrustedUrl(
                paymentUrl,
                PAYMENT_HOST,
                "Ссылка оплаты Tribute"
        );

        validateTrustedUrl(
                webappPaymentUrl,
                TELEGRAM_HOST,
                "Telegram-ссылка оплаты Tribute"
        );
    }

    private static void validateUuid(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException(
                    "Идентификатор заказа Tribute не должен быть null"
            );
        }
    }

    private static void validateTrustedUrl(
            URI url,
            String expectedHost,
            String fieldName
    ) {
        if (url == null) {
            throw new IllegalArgumentException(
                    fieldName + " не должна быть null"
            );
        }

        if (!url.isAbsolute()) {
            throw new IllegalArgumentException(
                    fieldName + " должна быть абсолютной"
            );
        }

        if (!"https".equalsIgnoreCase(url.getScheme())) {
            throw new IllegalArgumentException(
                    fieldName + " должна использовать HTTPS"
            );
        }

        if (url.getHost() == null
                || !expectedHost.equalsIgnoreCase(url.getHost())) {
            throw new IllegalArgumentException(
                    fieldName + " содержит недоверенный домен"
            );
        }

        if (url.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    fieldName + " не должна содержать учётные данные"
            );
        }

        if (url.getPort() != -1 && url.getPort() != 443) {
            throw new IllegalArgumentException(
                    fieldName + " должна использовать стандартный HTTPS-порт"
            );
        }

        if (url.getRawPath() == null
                || url.getRawPath().isBlank()
                || "/".equals(url.getRawPath())) {
            throw new IllegalArgumentException(
                    fieldName + " должна содержать путь"
            );
        }

        if (url.getFragment() != null) {
            throw new IllegalArgumentException(
                    fieldName + " не должна содержать фрагмент"
            );
        }

        if (url.toString().length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " не должна превышать 2048 символов"
            );
        }
    }
}