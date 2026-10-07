package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.dto.LoginResponse;
import com.translatelab.backend.auth.dto.SessionTokens;
import com.translatelab.backend.auth.entity.RefreshSession;
import com.translatelab.backend.auth.entity.RefreshToken;
import com.translatelab.backend.auth.exception.InvalidRefreshTokenException;
import com.translatelab.backend.auth.repository.RefreshSessionRepository;
import com.translatelab.backend.auth.repository.RefreshTokenRepository;
import com.translatelab.backend.config.RefreshTokenProperties;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshSessionRepository sessionRepository;
    private final RefreshTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AccountTokenHasher hasher;
    private final JwtService jwtService;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshSessionRepository sessionRepository,
                               RefreshTokenRepository tokenRepository,
                               UserRepository userRepository, AccountTokenHasher hasher,
                               JwtService jwtService, RefreshTokenProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.hasher = hasher;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Caller holds the user lock after checking the password. */
    @Transactional
    public SessionTokens open(User user) {
        Instant now = clock.instant();
        RefreshSession session = sessionRepository.saveAndFlush(
                new RefreshSession(user, now, now.plus(properties.sessionTtl()))
        );
        return issue(user, session, now);
    }

    // Replay revocation must commit even though the HTTP result is 401.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public SessionTokens rotate(String rawToken) {
        String hash = requireHash(rawToken);
        User user = lockOwner(hash);
        RefreshToken token = tokenRepository.findById(hash)
                .orElseThrow(InvalidRefreshTokenException::new);
        RefreshSession session = token.getSession();
        Instant now = clock.instant();
        if (!session.isActive(user, now)) {
            throw new InvalidRefreshTokenException();
        }
        if (token.getConsumedAt() != null) {
            session.revoke(now);
            throw new InvalidRefreshTokenException();
        }
        token.consume(now);
        tokenRepository.flush();
        return issue(user, session, now);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            return;
        }
        String hash = hasher.hash(rawToken);
        UUID ownerId = tokenRepository.findOwnerId(hash).orElse(null);
        if (ownerId == null || userRepository.findByIdForUpdate(ownerId).isEmpty()) {
            return;
        }
        tokenRepository.findById(hash).ifPresent(token -> token.getSession().revoke(clock.instant()));
    }

    @Scheduled(fixedDelayString = "${app.refresh-token.cleanup-interval:1h}")
    @Transactional
    public void deleteExpiredSessions() {
        // Expired families are deleted with their token history by the DB cascade.
        // Keep consumed hashes until expiry so replay can still be detected.
        sessionRepository.deleteExpiredBatch(clock.instant());
    }

    private String requireHash(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new InvalidRefreshTokenException();
        }
        return hasher.hash(rawToken);
    }

    private User lockOwner(String hash) {
        UUID userId = tokenRepository.findOwnerId(hash)
                .orElseThrow(InvalidRefreshTokenException::new);
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
    }

    private SessionTokens issue(User user, RefreshSession session, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.saveAndFlush(new RefreshToken(hasher.hash(rawToken), session, now));
        LoginResponse response = new LoginResponse(
                jwtService.generateAccessToken(user, session.getId()),
                "Bearer", jwtService.getAccessTokenTtlSeconds()
        );
        return new SessionTokens(response, rawToken, session.getExpiresAt());
    }
}
