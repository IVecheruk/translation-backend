package com.translatelab.backend.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
        @DefaultValue("7d") @NotNull Duration sessionTtl,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue("Lax") String cookieSameSite
) {
    public RefreshTokenProperties {
        if (sessionTtl == null || sessionTtl.compareTo(Duration.ofSeconds(1)) < 0
                || sessionTtl.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("Refresh session TTL must be between 1 second and 30 days");
        }
        if (!"Strict".equals(cookieSameSite) && !"Lax".equals(cookieSameSite)
                && !"None".equals(cookieSameSite)) {
            throw new IllegalArgumentException("Invalid refresh cookie SameSite policy");
        }
        if ("None".equals(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException("SameSite=None requires a Secure cookie");
        }
    }
}
