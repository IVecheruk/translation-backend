package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.entity.AccountActionToken;
import com.translatelab.backend.auth.entity.AccountActionTokenType;
import com.translatelab.backend.auth.repository.AccountActionTokenRepository;
import com.translatelab.backend.config.AccountSecurityProperties;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActionTokenIssuerTest {

    @Test
    void shouldPersistOnlyHashAndPublishRawTokenForAfterCommitDelivery() {
        Instant now = Instant.parse("2026-08-10T12:00:00Z");
        AccountActionTokenRepository repository =
                mock(AccountActionTokenRepository.class);
        ApplicationEventPublisher publisher =
                mock(ApplicationEventPublisher.class);
        User user = mock(User.class);
        UUID userId = UUID.fromString(
                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
        );
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("user@example.com");
        AccountTokenHasher hasher = new AccountTokenHasher();
        AccountActionTokenIssuer issuer = new AccountActionTokenIssuer(
                repository,
                hasher,
                new AccountSecurityProperties(
                        false,
                        URI.create("http://localhost:3000"),
                        Duration.ofHours(24),
                        Duration.ofMinutes(30)
                ),
                publisher,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        issuer.issue(user, AccountActionTokenType.PASSWORD_RESET);

        ArgumentCaptor<AccountActionToken> tokenCaptor =
                ArgumentCaptor.forClass(AccountActionToken.class);
        ArgumentCaptor<AccountTokenIssuedEvent> eventCaptor =
                ArgumentCaptor.forClass(AccountTokenIssuedEvent.class);
        verify(repository).save(tokenCaptor.capture());
        verify(repository).consumeOpenForUserAndType(
                userId,
                AccountActionTokenType.PASSWORD_RESET,
                now
        );
        verify(publisher).publishEvent(eventCaptor.capture());
        AccountActionToken stored = tokenCaptor.getValue();
        AccountTokenIssuedEvent event = eventCaptor.getValue();

        assertEquals(64, stored.getTokenHash().length());
        assertFalse(stored.getTokenHash().contains(event.rawToken()));
        assertEquals(stored.getTokenHash(), hasher.hash(event.rawToken()));
        assertEquals(now.plus(Duration.ofMinutes(30)), stored.getExpiresAt());
        assertEquals("user@example.com", event.email());
    }
}
