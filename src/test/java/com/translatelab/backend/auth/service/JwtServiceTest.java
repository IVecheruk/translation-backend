package com.translatelab.backend.auth.service;

import com.translatelab.backend.config.JwtConfig;
import com.translatelab.backend.config.JwtProperties;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void shouldGenerateDecodableAccessTokenWithExpectedClaims() {
        JwtProperties properties = new JwtProperties(
                createSecret(),
                ACCESS_TOKEN_TTL
        );
        JwtConfig config = new JwtConfig();
        SecretKey secretKey = config.jwtSecretKey(properties);
        JwtEncoder encoder = config.jwtEncoder(secretKey, properties);
        UserRepository userRepository = mock(UserRepository.class);
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) config.jwtDecoder(
                secretKey,
                properties,
                userRepository
        );
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        JwtService jwtService = new JwtService(
                encoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        UUID userId = UUID.fromString(
                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.isEmailVerified()).thenReturn(true);
        when(user.getAuthVersion()).thenReturn(3L);
        when(userRepository.findById(userId))
                .thenReturn(java.util.Optional.of(user));

        String tokenValue = jwtService.generateAccessToken(user);

        Jwt jwt = decoder.decode(tokenValue);

        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals("user@example.com", jwt.getClaimAsString("email"));
        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals("JWT", jwt.getHeaders().get("typ"));
        assertEquals("primary", jwt.getHeaders().get("kid"));
        assertEquals(
                "https://api.translatelab.local",
                jwt.getIssuer().toString()
        );
        assertEquals(
                java.util.List.of("translation-frontend"),
                jwt.getAudience()
        );
        assertEquals(true, jwt.getClaimAsBoolean("email_verified"));
        assertEquals(3L, ((Number) jwt.getClaim("auth_version")).longValue());
        assertEquals(
                ACCESS_TOKEN_TTL,
                Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())
        );
        assertEquals(NOW, jwt.getIssuedAt());
    }

    @Test
    void shouldReturnAccessTokenTtlInSeconds() {
        JwtProperties properties = new JwtProperties(
                createSecret(),
                ACCESS_TOKEN_TTL
        );
        JwtEncoder encoder = mock(JwtEncoder.class);
        JwtService jwtService = new JwtService(
                encoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertEquals(
                ACCESS_TOKEN_TTL.toSeconds(),
                jwtService.getAccessTokenTtlSeconds()
        );
    }

    private String createSecret() {
        byte[] secretBytes = new byte[32];

        for (int index = 0; index < secretBytes.length; index++) {
            secretBytes[index] = (byte) (index + 1);
        }

        return Base64.getEncoder().encodeToString(secretBytes);
    }
}
