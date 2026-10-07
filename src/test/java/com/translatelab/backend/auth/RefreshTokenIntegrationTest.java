package com.translatelab.backend.auth;

import com.translatelab.backend.auth.dto.LoginRequest;
import com.translatelab.backend.auth.dto.SessionTokens;
import com.translatelab.backend.auth.entity.AccountActionToken;
import com.translatelab.backend.auth.entity.AccountActionTokenType;
import com.translatelab.backend.auth.exception.InvalidCredentialsException;
import com.translatelab.backend.auth.exception.InvalidRefreshTokenException;
import com.translatelab.backend.auth.service.AccountRecoveryService;
import com.translatelab.backend.auth.service.AccountTokenHasher;
import com.translatelab.backend.auth.service.JwtService;
import com.translatelab.backend.auth.service.LoginService;
import com.translatelab.backend.auth.service.RefreshCookieService;
import com.translatelab.backend.auth.service.RefreshTokenService;
import com.translatelab.backend.auth.service.SessionRevocationService;
import com.translatelab.backend.auth.repository.AccountActionTokenRepository;
import com.translatelab.backend.auth.repository.RefreshSessionRepository;
import com.translatelab.backend.config.JwtProperties;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.repository.UserRepository;
import com.translatelab.backend.user.repository.UserProfileRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.refresh-token.session-ttl=7d",
        "app.refresh-token.cookie-secure=false",
        "app.refresh-token.cleanup-interval=24h",
        "app.security.rate-limit.login-per-minute=1000"
})
@AutoConfigureMockMvc
@Import(RefreshTokenIntegrationTest.TimeConfig.class)
class RefreshTokenIntegrationTest {
    @Autowired private LoginService loginService;
    @Autowired private RefreshTokenService refreshService;
    @Autowired private SessionRevocationService revocationService;
    @Autowired private AccountRecoveryService recoveryService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository profileRepository;
    @Autowired private AccountActionTokenRepository actionTokenRepository;
    @Autowired private RefreshSessionRepository sessionRepository;
    @Autowired private AccountTokenHasher hasher;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtEncoder encoder;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private MutableClock testClock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    private UUID userId;
    private String email;

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private volatile Instant current = Instant.now();
        void set(Instant now) { current = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
        @Override public Instant instant() { return current; }
    }

    @BeforeEach
    void createUser() {
        testClock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        email = "refresh-" + UUID.randomUUID() + "@example.com";
        User user = new User(email, passwordEncoder.encode("LocalTest123!"));
        user.verifyEmail(testClock.instant());
        user = userRepository.saveAndFlush(user);
        userId = user.getId();
        profileRepository.saveAndFlush(new UserProfile(user));
    }

    @AfterEach
    void cleanUp() {
        testClock.set(Instant.now());
        if (userId != null) {
            profileRepository.deleteById(userId);
            jdbc.update("DELETE FROM account_action_tokens WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
    }

    @Test
    void storesOnlyHashesAndRotatesWithoutExtendingAbsoluteLifetime() {
        SessionTokens first = login();
        assertEquals(43, first.refreshToken().length());
        assertEquals(hasher.hash(first.refreshToken()), jdbc.queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE session_id = ?",
                String.class, sessionId(first)));
        assertEquals(testClock.instant().plus(Duration.ofDays(7)), first.expiresAt());
        assertFalse(first.toString().contains(first.refreshToken()));
        testClock.set(testClock.instant().plusSeconds(60));
        SessionTokens second = refreshService.rotate(first.refreshToken());
        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertEquals(first.expiresAt(), second.expiresAt());
        assertEquals(sessionId(first), sessionId(second));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE session_id = ?", Integer.class, sessionId(first)));
    }

    @Test
    void replayCommitsFamilyRevocationAndInvalidatesIssuedAccessTokens() {
        SessionTokens first = login();
        SessionTokens second = refreshService.rotate(first.refreshToken());
        UUID sid = sessionId(first);
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(first.refreshToken()));
        assertNotNull(sessionRepository.findById(sid).orElseThrow().getRevokedAt());
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(second.refreshToken()));
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,
                () -> decoder.decode(second.response().accessToken()));
    }

    @Test
    void logoutRevokesOnlyCurrentDeviceAndItsAccessToken() {
        SessionTokens first = login();
        SessionTokens otherDevice = login();
        refreshService.logout(first.refreshToken());
        refreshService.logout(first.refreshToken());
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(first.refreshToken()));
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,
                () -> decoder.decode(first.response().accessToken()));
        assertDoesNotThrow(() -> refreshService.rotate(otherDevice.refreshToken()));
    }

    @Test
    void logoutAllRevokesEveryDevice() {
        SessionTokens first = login();
        SessionTokens second = login();
        revocationService.revokeAll(userId);
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(first.refreshToken()));
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(second.refreshToken()));
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,
                () -> decoder.decode(first.response().accessToken()));
        assertDoesNotThrow(this::login);
    }

    @Test
    void passwordResetRevokesRefreshSessionsAndOldPassword() {
        SessionTokens first = login();
        String resetToken = "r".repeat(43);
        AccountActionToken actionToken = actionTokenRepository.saveAndFlush(AccountActionToken.active(
                userRepository.findById(userId).orElseThrow(), AccountActionTokenType.PASSWORD_RESET,
                hasher.hash(resetToken), testClock.instant(), testClock.instant().plusSeconds(600)
        ));
        testClock.set(actionToken.getCreatedAt().plusSeconds(1));
        recoveryService.resetPassword(resetToken, "DifferentTest123!");
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(first.refreshToken()));
        assertThrows(InvalidCredentialsException.class, this::login);
        assertDoesNotThrow(() -> loginService.login(new LoginRequest(email, "DifferentTest123!")));
    }

    @Test
    void expirationIsExactAndCleanupDeletesTokenHistory() {
        SessionTokens first = login();
        UUID sid = sessionId(first);
        testClock.set(first.expiresAt());
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(first.refreshToken()));
        refreshService.deleteExpiredSessions();
        assertTrue(sessionRepository.findById(sid).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE session_id = ?",
                Integer.class, sid));
    }

    @Test
    void unknownOrMalformedSecretsDoNotRevokeOtherSessions() {
        SessionTokens first = login();
        for (String raw : new String[]{"", "not-a-token", "z".repeat(43), "a".repeat(257)}) {
            assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(raw));
            refreshService.logout(raw);
        }
        assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(null));
        refreshService.logout(null);
        assertDoesNotThrow(() -> refreshService.rotate(first.refreshToken()));
    }

    @Test
    void concurrentRefreshConsumesOnceAndCommitsReplayRevocation() throws Exception {
        SessionTokens first = login();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<SessionTokens> action = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    return refreshService.rotate(first.refreshToken());
                } catch (InvalidRefreshTokenException expected) {
                    return null;
                }
            };
            var a = executor.submit(action);
            var b = executor.submit(action);
            start.countDown();
            SessionTokens resultA = a.get(15, TimeUnit.SECONDS);
            SessionTokens resultB = b.get(15, TimeUnit.SECONDS);
            assertTrue((resultA == null) != (resultB == null));
            SessionTokens winner = resultA == null ? resultB : resultA;
            assertThrows(InvalidRefreshTokenException.class, () -> refreshService.rotate(winner.refreshToken()));
        }
    }

    @Test
    void httpLoginRefreshAndLogoutWorkWithCookiesAndExpiredAccess() throws Exception {
        var loginResult = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest(email, "LocalTest123!"))))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(RefreshCookieService.COOKIE_NAME, true))
                .andExpect(cookie().secure(RefreshCookieService.COOKIE_NAME, false))
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
        Cookie firstCookie = loginResult.getResponse().getCookie(RefreshCookieService.COOKIE_NAME);
        String firstAccess = mapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asString();
        mvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        UUID sid = UUID.fromString(decoder.decode(firstAccess).getClaimAsString("sid"));
        String expired = new JwtService(encoder, jwtProperties,
                Clock.fixed(testClock.instant().minus(Duration.ofHours(2)), ZoneOffset.UTC))
                .generateAccessToken(userRepository.findById(userId).orElseThrow(), sid);
        mvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        var refreshResult = mvc.perform(post("/api/auth/refresh").cookie(firstCookie)
                        .header("X-Refresh-Request", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
        Cookie nextCookie = refreshResult.getResponse().getCookie(RefreshCookieService.COOKIE_NAME);
        assertNotEquals(firstCookie.getValue(), nextCookie.getValue());
        String nextAccess = mapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken").asString();
        mvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + nextAccess))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(nextCookie).header("X-Refresh-Request", "true"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(RefreshCookieService.COOKIE_NAME, 0));
        mvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + nextAccess))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(nextCookie).header("X-Refresh-Request", "true"))
                .andExpect(status().isUnauthorized());
    }

    private SessionTokens login() {
        return loginService.login(new LoginRequest(email, "LocalTest123!"));
    }

    private UUID sessionId(SessionTokens tokens) {
        return UUID.fromString(decoder.decode(tokens.response().accessToken()).getClaimAsString("sid"));
    }

}
