package com.translatelab.backend.auth.security;

import com.translatelab.backend.auth.repository.RefreshSessionRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.UUID;

public class RefreshSessionJwtValidator implements OAuth2TokenValidator<Jwt> {
    private final RefreshSessionRepository repository;
    private final Clock clock;

    public RefreshSessionJwtValidator(RefreshSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // Access tokens issued before this migration remain valid until expiry.
        if (!jwt.hasClaim("sid")) {
            return OAuth2TokenValidatorResult.success();
        }
        try {
            UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
            UUID userId = UUID.fromString(jwt.getSubject());
            if (repository.isActive(sessionId, userId, clock.instant())) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (RuntimeException ignored) {
            // Fail closed on invalid claims or unavailable session storage.
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Session has expired or been revoked", null)
        );
    }
}
