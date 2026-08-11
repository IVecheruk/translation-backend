package com.translatelab.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.account-security")
public record AccountSecurityProperties(
        @DefaultValue("false") boolean emailDeliveryEnabled,
        @DefaultValue("noreply@translatelab.local") String senderEmail,
        @DefaultValue("http://localhost:3000") @NotNull URI frontendBaseUrl,
        @DefaultValue("24h") @NotNull Duration emailVerificationTtl,
        @DefaultValue("30m") @NotNull Duration passwordResetTtl
) {
    @ConstructorBinding
    public AccountSecurityProperties {
        if (emailDeliveryEnabled && (senderEmail == null
                || !senderEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            throw new IllegalArgumentException(
                    "Для доставки писем требуется корректный sender email"
            );
        }
        validateBaseUrl(frontendBaseUrl);
        validateTtl(emailVerificationTtl, "Срок подтверждения email");
        validateTtl(passwordResetTtl, "Срок сброса пароля");
    }

    public AccountSecurityProperties(
            boolean emailDeliveryEnabled,
            URI frontendBaseUrl,
            Duration emailVerificationTtl,
            Duration passwordResetTtl
    ) {
        this(
                emailDeliveryEnabled,
                "noreply@translatelab.local",
                frontendBaseUrl,
                emailVerificationTtl,
                passwordResetTtl
        );
    }

    private static void validateBaseUrl(URI uri) {
        if (uri == null) {
            return;
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()));
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        if ((!secure && !localHttp)
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Frontend URL должен быть HTTPS или локальным HTTP URL"
            );
        }
    }

    private static void validateTtl(Duration ttl, String name) {
        if (ttl != null && (ttl.isZero() || ttl.isNegative()
                || ttl.compareTo(Duration.ofDays(7)) > 0)) {
            throw new IllegalArgumentException(
                    name + " должен быть положительным и не больше 7 дней"
            );
        }
    }
}
