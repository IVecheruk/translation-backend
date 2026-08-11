package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.entity.AccountActionToken;
import com.translatelab.backend.auth.entity.AccountActionTokenType;
import com.translatelab.backend.auth.exception.InvalidAccountActionTokenException;
import com.translatelab.backend.auth.repository.AccountActionTokenRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final String RAW_TOKEN =
            "0123456789012345678901234567890123456789012";
    private static final UUID USER_ID = UUID.fromString(
            "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
    );

    @Mock UserRepository userRepository;
    @Mock AccountActionTokenRepository tokenRepository;
    @Mock AccountActionTokenIssuer tokenIssuer;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void shouldKeepPasswordResetRequestGenericForMissingAccount() {
        when(userRepository.findByEmailForUpdate("missing@example.com"))
                .thenReturn(Optional.empty());

        service().requestPasswordReset(" Missing@Example.com ");

        verify(tokenIssuer, never()).issue(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldVerifyEmailWithSingleUseToken() {
        User user = mock(User.class);
        prepareUserLock(user);
        AccountTokenHasher hasher = new AccountTokenHasher();
        AccountActionToken token = AccountActionToken.active(
                user,
                AccountActionTokenType.EMAIL_VERIFICATION,
                hasher.hash(RAW_TOKEN),
                NOW.minusSeconds(60),
                NOW.plusSeconds(60)
        );
        when(tokenRepository.findOpenByHash(hasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));
        when(tokenRepository.findOpenByHashForUpdate(hasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        service().confirmEmail(RAW_TOKEN);

        verify(user).verifyEmail(NOW);
        assertEquals(NOW, token.getConsumedAt());
    }

    @Test
    void shouldResetPasswordAndRevokeSessionsThroughDomainOperation() {
        User user = mock(User.class);
        prepareUserLock(user);
        AccountTokenHasher hasher = new AccountTokenHasher();
        AccountActionToken token = AccountActionToken.active(
                user,
                AccountActionTokenType.PASSWORD_RESET,
                hasher.hash(RAW_TOKEN),
                NOW.minusSeconds(60),
                NOW.plusSeconds(60)
        );
        when(tokenRepository.findOpenByHash(hasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));
        when(tokenRepository.findOpenByHashForUpdate(hasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password"))
                .thenReturn("new-password-hash");

        service().resetPassword(RAW_TOKEN, "new-password");

        verify(user).changePassword("new-password-hash");
        assertEquals(NOW, token.getConsumedAt());
    }

    @Test
    void shouldRejectExpiredOrWrongPurposeTokenSafely() {
        User user = mock(User.class);
        prepareUserLock(user);
        AccountTokenHasher hasher = new AccountTokenHasher();
        AccountActionToken token = AccountActionToken.active(
                user,
                AccountActionTokenType.PASSWORD_RESET,
                hasher.hash(RAW_TOKEN),
                NOW.minusSeconds(120),
                NOW.minusSeconds(1)
        );
        when(tokenRepository.findOpenByHash(hasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));
        when(tokenRepository.findOpenByHashForUpdate(hasher.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        assertThrows(
                InvalidAccountActionTokenException.class,
                () -> service().confirmEmail(RAW_TOKEN)
        );
    }

    private AccountRecoveryService service() {
        return new AccountRecoveryService(
                userRepository,
                tokenRepository,
                tokenIssuer,
                new AccountTokenHasher(),
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void prepareUserLock(User user) {
        when(user.getId()).thenReturn(USER_ID);
        when(userRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(user));
    }
}
