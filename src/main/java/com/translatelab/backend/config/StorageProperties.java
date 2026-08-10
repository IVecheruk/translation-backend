package com.translatelab.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;
import java.util.Locale;

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
        String bucket,

        boolean allowInsecureHttp,

        @NotNull
        @Size(min = 1)
        List<@NotBlank String> allowedHosts
) {

    @ConstructorBinding
    public StorageProperties {
        if (endpoint != null) {
            String scheme = endpoint.getScheme();
            if (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException(
                        "MinIO endpoint должен использовать схему http или https"
                );
            }
            if (endpoint.getHost() == null
                    || endpoint.getHost().isBlank()
                    || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null
                    || endpoint.getFragment() != null
                    || !isRootPath(endpoint.getPath())) {
                throw new IllegalArgumentException(
                        "MinIO endpoint должен быть сетевым origin без credentials, path, query и fragment"
                );
            }
            if ("http".equalsIgnoreCase(scheme) && !allowInsecureHttp) {
                throw new IllegalArgumentException(
                        "HTTP для MinIO разрешён только явной локальной настройкой"
                );
            }

            allowedHosts = allowedHosts == null
                    ? null
                    : allowedHosts.stream()
                            .map(String::strip)
                            .map(host -> host.toLowerCase(Locale.ROOT))
                            .distinct()
                            .toList();
            if (allowedHosts != null
                    && !allowedHosts.contains(
                            endpoint.getHost().toLowerCase(Locale.ROOT)
                    )) {
                throw new IllegalArgumentException(
                        "Хост MinIO endpoint отсутствует в списке разрешённых"
                );
            }
        }
    }

    public StorageProperties(
            URI endpoint,
            String accessKey,
            String secretKey,
            String bucket
    ) {
        this(
                endpoint,
                accessKey,
                secretKey,
                bucket,
                endpoint != null
                        && "http".equalsIgnoreCase(endpoint.getScheme()),
                endpoint == null || endpoint.getHost() == null
                        ? List.of("localhost")
                        : List.of(endpoint.getHost())
        );
    }

    private static boolean isRootPath(String path) {
        return path == null || path.isEmpty() || "/".equals(path);
    }
}
