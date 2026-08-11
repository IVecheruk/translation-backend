package com.translatelab.backend.user.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSecurityStateTest {

    @Test
    void shouldVerifyEmailIdempotently() {
        User user = new User("user@example.com", "password-hash");
        Instant first = Instant.parse("2026-08-10T12:00:00Z");

        user.verifyEmail(first);
        user.verifyEmail(first.plusSeconds(10));

        assertAll(
                () -> assertTrue(user.isEmailVerified()),
                () -> assertEquals(first, user.getEmailVerifiedAt())
        );
    }

    @Test
    void shouldChangePasswordAndRevokeAllExistingTokens() {
        User user = new User("user@example.com", "old-hash");
        assertFalse(user.isEmailVerified());

        user.changePassword("new-hash");
        user.revokeSessions();

        assertAll(
                () -> assertEquals("new-hash", user.getPasswordHash()),
                () -> assertEquals(2L, user.getAuthVersion())
        );
    }
}
