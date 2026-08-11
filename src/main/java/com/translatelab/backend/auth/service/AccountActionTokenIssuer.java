package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.entity.AccountActionToken;
import com.translatelab.backend.auth.entity.AccountActionTokenType;
import com.translatelab.backend.auth.repository.AccountActionTokenRepository;
import com.translatelab.backend.config.AccountSecurityProperties;
import com.translatelab.backend.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AccountActionTokenIssuer {

    private final AccountActionTokenRepository repository;
    private final AccountTokenHasher tokenHasher;
    private final AccountSecurityProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountActionTokenIssuer(
            AccountActionTokenRepository repository,
            AccountTokenHasher tokenHasher,
            AccountSecurityProperties properties,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.repository = repository;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public void issue(User user, AccountActionTokenType type) {
        Instant now = clock.instant();
        repository.consumeOpenForUserAndType(user.getId(), type, now);
        String rawToken = generate();
        Duration ttl = type == AccountActionTokenType.EMAIL_VERIFICATION
                ? properties.emailVerificationTtl()
                : properties.passwordResetTtl();
        repository.save(AccountActionToken.active(
                user,
                type,
                tokenHasher.hash(rawToken),
                now,
                now.plus(ttl)
        ));
        eventPublisher.publishEvent(new AccountTokenIssuedEvent(
                user.getEmail(),
                type,
                rawToken
        ));
    }

    private String generate() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
