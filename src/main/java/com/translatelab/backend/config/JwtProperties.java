package com.translatelab.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.net.URI;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(

        @NotBlank
        String secret,

        @NotNull
        Duration accessTokenTtl,

        @NotBlank
        String issuer,

        @NotBlank
        String audience,

        @NotBlank
        String keyId
) {

    private static final Duration MAXIMUM_ACCESS_TOKEN_TTL =
            Duration.ofHours(1);

    @ConstructorBinding
    public JwtProperties {
        if (accessTokenTtl != null
                && (accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(MAXIMUM_ACCESS_TOKEN_TTL) > 0)) {
            throw new IllegalArgumentException(
                    "Срок действия access token должен быть больше нуля "
                            + "и не превышать один час"
            );
        }
        if (issuer != null) {
            URI issuerUri;
            try {
                issuerUri = URI.create(issuer);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Некорректный JWT issuer", exception);
            }
            if (!issuerUri.isAbsolute()
                    || issuerUri.getHost() == null
                    || issuerUri.getUserInfo() != null
                    || issuerUri.getFragment() != null) {
                throw new IllegalArgumentException("Некорректный JWT issuer");
            }
        }
        if (keyId != null && !keyId.matches("^[A-Za-z0-9._-]{1,64}$")) {
            throw new IllegalArgumentException("Некорректный JWT key ID");
        }
    }

    public JwtProperties(String secret, Duration accessTokenTtl) {
        this(
                secret,
                accessTokenTtl,
                "https://api.translatelab.local",
                "translation-frontend",
                "primary"
        );
    }
}
