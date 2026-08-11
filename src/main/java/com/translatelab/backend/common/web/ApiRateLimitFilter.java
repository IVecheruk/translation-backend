package com.translatelab.backend.common.web;

import com.translatelab.backend.config.ApiRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimitProperties properties;
    private final RateLimitResponseWriter responseWriter;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows =
            new ConcurrentHashMap<>();

    public ApiRateLimitFilter(
            ApiRateLimitProperties properties,
            RateLimitResponseWriter responseWriter,
            Clock clock
    ) {
        this.properties = properties;
        this.responseWriter = responseWriter;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return policy(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Policy policy = policy(request);
        long epochSecond = clock.instant().getEpochSecond();
        long minute = epochSecond / 60;
        String key = policy.name()
                + ':' + request.getRemoteAddr()
                + ':' + authenticatedAccount();
        if (!windows.containsKey(key)
                && windows.size() >= properties.maximumTrackedKeys()) {
            key = policy.name() + ":overflow";
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

        if (window.requests() > policy.limit()) {
            responseWriter.write(
                    request,
                    response,
                    Math.max(1, 60 - epochSecond % 60)
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String authenticatedAccount() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "public";
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "public" : name;
    }

    private Policy policy(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        return policies().get(request.getRequestURI());
    }

    private Map<String, Policy> policies() {
        return Map.of(
                "/api/auth/register",
                new Policy("registration", properties.registrationPerMinute()),
                "/api/auth/login",
                new Policy("login", properties.loginPerMinute()),
                "/api/documents/upload",
                new Policy("document-upload", properties.documentUploadPerMinute()),
                "/api/subscription-purchases",
                new Policy("checkout", properties.checkoutPerMinute()),
                "/api/auth/email-verification/request",
                new Policy("email-verification-request", properties.accountEmailRequestPerMinute()),
                "/api/auth/email-verification/confirm",
                new Policy("email-verification-confirm", properties.accountTokenConfirmationPerMinute()),
                "/api/auth/password-reset/request",
                new Policy("password-reset-request", properties.accountEmailRequestPerMinute()),
                "/api/auth/password-reset/confirm",
                new Policy("password-reset-confirm", properties.accountTokenConfirmationPerMinute())
        );
    }

    private record Policy(String name, int limit) {}

    private record Window(long minute, int requests) {}
}
