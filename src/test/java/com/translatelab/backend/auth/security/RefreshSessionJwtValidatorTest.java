package com.translatelab.backend.auth.security;

import com.translatelab.backend.auth.repository.RefreshSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RefreshSessionJwtValidatorTest {
    private final Instant now = Instant.parse("2026-10-07T00:00:00Z");
    private final RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
    private final RefreshSessionJwtValidator validator = new RefreshSessionJwtValidator(
            repository, Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void preservesLegacyAccessTokens() {
        assertFalse(validator.validate(jwt(null)).hasErrors());
        verifyNoInteractions(repository);
    }

    @Test
    void checksSessionAndOwnerAndRejectsRevocation() {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(ownerId.toString()).claim("sid", sessionId.toString()).build();
        when(repository.isActive(sessionId, ownerId, now)).thenReturn(true, false);
        assertFalse(validator.validate(token).hasErrors());
        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void rejectsMalformedSessionIdAndUnavailableStorage() {
        assertTrue(validator.validate(jwt("invalid")).hasErrors());
        String sessionId = UUID.randomUUID().toString();
        when(repository.isActive(any(), any(), any())).thenThrow(new IllegalStateException("unavailable"));
        assertTrue(validator.validate(jwt(sessionId)).hasErrors());
    }

    private Jwt jwt(String sessionId) {
        Jwt.Builder builder = Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(UUID.randomUUID().toString());
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        return builder.build();
    }
}
