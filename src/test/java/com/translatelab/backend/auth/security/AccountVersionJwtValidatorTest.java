package com.translatelab.backend.auth.security;

import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountVersionJwtValidatorTest {

    private static final UUID USER_ID = UUID.fromString(
            "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
    );

    @Test
    void shouldAcceptCurrentAccountSecurityState() {
        UserRepository repository = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getAuthVersion()).thenReturn(4L);
        when(user.isEmailVerified()).thenReturn(true);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertTrue(validator(repository).validate(jwt(4, true, "primary"))
                .hasErrors() == false);
    }

    @Test
    void shouldRejectRevokedVersionWrongKeyAndStaleVerification() {
        UserRepository repository = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getAuthVersion()).thenReturn(5L);
        when(user.isEmailVerified()).thenReturn(true);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertTrue(validator(repository).validate(jwt(4, true, "primary"))
                .hasErrors());
        assertTrue(validator(repository).validate(jwt(5, true, "retired"))
                .hasErrors());
        assertTrue(validator(repository).validate(jwt(5, false, "primary"))
                .hasErrors());
    }

    private AccountVersionJwtValidator validator(UserRepository repository) {
        return new AccountVersionJwtValidator(repository, "primary");
    }

    private Jwt jwt(long version, boolean verified, String keyId) {
        Instant now = Instant.parse("2026-08-10T12:00:00Z");
        return new Jwt(
                "token",
                now,
                now.plusSeconds(900),
                Map.of("alg", "HS256", "kid", keyId),
                Map.of(
                        "sub", USER_ID.toString(),
                        "auth_version", version,
                        "email_verified", verified
                )
        );
    }
}
