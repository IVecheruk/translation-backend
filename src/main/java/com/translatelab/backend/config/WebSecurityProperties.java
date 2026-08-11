package com.translatelab.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record WebSecurityProperties(
        @DefaultValue("false") boolean corsEnabled,
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue("false") boolean openApiPublicAccess
) {
    public WebSecurityProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (corsEnabled && allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException(
                    "Для включённого CORS требуется точный список origins"
            );
        }
        allowedOrigins.forEach(WebSecurityProperties::validateOrigin);
    }

    private static void validateOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Некорректный CORS origin", exception);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp)
                || uri.getHost() == null
                || uri.getPath() != null && !uri.getPath().isEmpty()
                || uri.getQuery() != null
                || uri.getFragment() != null
                || uri.getUserInfo() != null
                || "*".equals(origin)) {
            throw new IllegalArgumentException(
                    "CORS origin должен быть точным HTTPS или локальным HTTP origin"
            );
        }
    }
}
