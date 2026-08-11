package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.entity.AccountActionToken;
import com.translatelab.backend.auth.entity.AccountActionTokenType;
import com.translatelab.backend.auth.exception.InvalidAccountActionTokenException;
import com.translatelab.backend.auth.repository.AccountActionTokenRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class AccountRecoveryService {

    private final UserRepository userRepository;
    private final AccountActionTokenRepository tokenRepository;
    private final AccountActionTokenIssuer tokenIssuer;
    private final AccountTokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountRecoveryService(
            UserRepository userRepository,
            AccountActionTokenRepository tokenRepository,
            AccountActionTokenIssuer tokenIssuer,
            AccountTokenHasher tokenHasher,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenIssuer = tokenIssuer;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void requestEmailVerification(String email) {
        userRepository.findByEmailForUpdate(normalize(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> tokenIssuer.issue(
                        user,
                        AccountActionTokenType.EMAIL_VERIFICATION
                ));
    }

    @Transactional
    public void confirmEmail(String rawToken) {
        Instant now = clock.instant();
        ValidatedToken validated = require(
                rawToken,
                AccountActionTokenType.EMAIL_VERIFICATION,
                now
        );
        validated.user().verifyEmail(now);
        validated.token().consume(now);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailForUpdate(normalize(email))
                .ifPresent(user -> tokenIssuer.issue(
                        user,
                        AccountActionTokenType.PASSWORD_RESET
                ));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        ValidatedToken validated = require(
                rawToken,
                AccountActionTokenType.PASSWORD_RESET,
                now
        );
        validated.user().changePassword(passwordEncoder.encode(newPassword));
        validated.token().consume(now);
    }

    private ValidatedToken require(
            String rawToken,
            AccountActionTokenType expectedType,
            Instant now
    ) {
        String tokenHash = tokenHasher.hash(rawToken);
        AccountActionToken candidate = tokenRepository
                .findOpenByHash(tokenHash)
                .orElseThrow(InvalidAccountActionTokenException::new);
        User user = userRepository.findByIdForUpdate(
                candidate.getUser().getId()
        ).orElseThrow(InvalidAccountActionTokenException::new);
        AccountActionToken token = tokenRepository
                .findOpenByHashForUpdate(tokenHash)
                .orElseThrow(InvalidAccountActionTokenException::new);
        if (token.getType() != expectedType
                || !token.getUser().getId().equals(user.getId())
                || !now.isBefore(token.getExpiresAt())) {
            throw new InvalidAccountActionTokenException();
        }
        return new ValidatedToken(token, user);
    }

    private String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private record ValidatedToken(AccountActionToken token, User user) {}
}
