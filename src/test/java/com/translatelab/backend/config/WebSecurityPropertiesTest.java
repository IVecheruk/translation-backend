package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSecurityPropertiesTest {

    @Test
    void shouldAllowOnlyExactSecureOrLocalOrigins() {
        WebSecurityProperties properties = new WebSecurityProperties(
                true,
                List.of("https://app.example.com", "http://localhost:3000"),
                false
        );

        assertEquals(2, properties.allowedOrigins().size());
        assertThrows(IllegalArgumentException.class, () ->
                new WebSecurityProperties(true, List.of("*"), false));
        assertThrows(IllegalArgumentException.class, () ->
                new WebSecurityProperties(
                        true,
                        List.of("http://app.example.com"),
                        false
                ));
    }

    @Test
    void shouldKeepCorsDisabledForSameOriginDeployment() {
        assertDoesNotThrow(() -> new WebSecurityProperties(
                false,
                List.of(),
                false
        ));
        assertThrows(IllegalArgumentException.class, () ->
                new WebSecurityProperties(true, List.of(), false));
    }
}
