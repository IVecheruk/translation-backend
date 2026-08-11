package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoragePropertiesSecurityTest {

    @Test
    void shouldAllowExactApprovedHttpsOrigin() {
        StorageProperties properties = properties(
                "https://storage.example.com:9000",
                false,
                List.of("STORAGE.EXAMPLE.COM")
        );

        assertEquals(
                List.of("storage.example.com"),
                properties.allowedHosts()
        );
    }

    @Test
    void shouldAllowHttpOnlyWhenExplicitlyEnabled() {
        properties("http://localhost:9000", true, List.of("localhost"));

        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        "http://localhost:9000",
                        false,
                        List.of("localhost")
                )
        );
    }

    @Test
    void shouldRejectUnsupportedSchemeAndUnapprovedHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        "ftp://storage.example.com",
                        false,
                        List.of("storage.example.com")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        "https://evil.example.com",
                        false,
                        List.of("storage.example.com")
                )
        );
    }

    @Test
    void shouldRejectCredentialsPathQueryAndFragment() {
        List<String> allowed = List.of("storage.example.com");

        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        "https://user@storage.example.com",
                        false,
                        allowed
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        "https://storage.example.com/api",
                        false,
                        allowed
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        "https://storage.example.com?x=1",
                        false,
                        allowed
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> properties(
                        "https://storage.example.com#fragment",
                        false,
                        allowed
                )
        );
    }

    private StorageProperties properties(
            String endpoint,
            boolean allowHttp,
            List<String> allowedHosts
    ) {
        return new StorageProperties(
                URI.create(endpoint),
                "backend-access",
                "backend-secret",
                "translation-documents",
                allowHttp,
                allowedHosts
        );
    }
}
