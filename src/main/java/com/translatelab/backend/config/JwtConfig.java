package com.translatelab.backend.config;

import com.translatelab.backend.auth.security.AccountVersionJwtValidator;
import com.translatelab.backend.auth.security.RefreshSessionJwtValidator;
import com.translatelab.backend.auth.repository.RefreshSessionRepository;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder()
                    .decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET должен содержать корректное Base64-значение",
                    exception
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET после декодирования должен содержать минимум 32 байта"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(
            SecretKey jwtSecretKey,
            JwtProperties properties
    ) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(jwtSecretKey)
                .algorithm(JWSAlgorithm.HS256)
                .keyID(properties.keyId())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties properties,
            UserRepository userRepository,
            RefreshSessionRepository sessionRepository,
            Clock clock
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(properties.audience())
                        ? org.springframework.security.oauth2.core
                        .OAuth2TokenValidatorResult.success()
                        : org.springframework.security.oauth2.core
                        .OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error(
                                        "invalid_token",
                                        "Некорректная аудитория access token",
                                        null
                                )
                        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator,
                new AccountVersionJwtValidator(
                        userRepository,
                        properties.keyId()
                ),
                new RefreshSessionJwtValidator(sessionRepository, clock)
        ));
        return decoder;
    }
}
