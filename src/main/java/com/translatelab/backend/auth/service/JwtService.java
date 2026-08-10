package com.translatelab.backend.auth.service;

import com.translatelab.backend.config.JwtProperties;
import com.translatelab.backend.user.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Clock;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtService(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(
                jwtProperties.accessTokenTtl()
        );

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .keyId(jwtProperties.keyId())
                .build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience()))
                .claim("email", user.getEmail())
                .claim("email_verified", user.isEmailVerified())
                .claim("auth_version", user.getAuthVersion())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwtEncoderParameters parameters =
                JwtEncoderParameters.from(header, claims);

        return jwtEncoder
                .encode(parameters)
                .getTokenValue();
    }

    public long getAccessTokenTtlSeconds() {
        return jwtProperties
                .accessTokenTtl()
                .toSeconds();
    }
}
