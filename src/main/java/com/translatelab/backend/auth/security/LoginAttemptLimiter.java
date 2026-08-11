package com.translatelab.backend.auth.security;

import com.translatelab.backend.common.exception.RequestRateLimitExceededException;
import com.translatelab.backend.config.ApiRateLimitProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptLimiter {

    private final ApiRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows =
            new ConcurrentHashMap<>();

    public LoginAttemptLimiter(
            ApiRateLimitProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;
    }

    public void check(String email, String remoteAddress) {
        long epochSecond = clock.instant().getEpochSecond();
        long minute = epochSecond / 60;
        String key = hash(email.strip().toLowerCase(Locale.ROOT))
                + ':' + (remoteAddress == null ? "unknown" : remoteAddress);
        if (!windows.containsKey(key)
                && windows.size() >= properties.maximumTrackedKeys()) {
            key = "overflow";
        }
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || current.minute() != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, current.requests() + 1);
        });
        if (windows.size() > properties.maximumTrackedKeys()) {
            windows.entrySet().removeIf(
                    entry -> entry.getValue().minute() < minute
            );
        }
        if (window.requests() > properties.loginPerMinute()) {
            throw new RequestRateLimitExceededException(
                    60 - epochSecond % 60
            );
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }

    private record Window(long minute, int requests) {}
}
