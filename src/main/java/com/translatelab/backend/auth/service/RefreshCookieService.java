package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.dto.SessionTokens;
import com.translatelab.backend.config.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class RefreshCookieService {
    public static final String COOKIE_NAME = "translatelab_refresh";
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshCookieService(RefreshTokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String read(HttpServletRequest request) {
        String value = null;
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                // Do not guess when conflicting cookies are supplied.
                if (value != null) {
                    return null;
                }
                value = cookie.getValue();
            }
        }
        return value;
    }

    public void write(HttpServletResponse response, SessionTokens tokens) {
        Duration remaining = Duration.between(clock.instant(), tokens.expiresAt());
        set(response, tokens.refreshToken(), remaining.isNegative() ? Duration.ZERO : remaining);
    }

    public void clear(HttpServletResponse response) {
        set(response, "", Duration.ZERO);
    }

    private void set(HttpServletResponse response, String value, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true).secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite()).path("/api/auth")
                .maxAge(maxAge).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
