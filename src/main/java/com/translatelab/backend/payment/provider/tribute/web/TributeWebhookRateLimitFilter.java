package com.translatelab.backend.payment.provider.tribute.web;

import com.translatelab.backend.config.TributeProperties;
import com.translatelab.backend.common.web.RateLimitResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributeWebhookRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH = "/api/payments/webhooks/tribute";

    private final TributeProperties properties;
    private final Clock clock;
    private final RateLimitResponseWriter responseWriter;
    private final ConcurrentHashMap<String, Window> windows =
            new ConcurrentHashMap<>();

    public TributeWebhookRateLimitFilter(
            TributeProperties properties,
            Clock clock,
            RateLimitResponseWriter responseWriter
    ) {
        this.properties = properties;
        this.clock = clock;
        this.responseWriter = responseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long epochSecond = clock.instant().getEpochSecond();
        long minute = epochSecond / 60;
        String client = request.getRemoteAddr();
        Window window = windows.compute(client, (key, current) -> {
            if (current == null || current.minute != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, current.requests + 1);
        });
        if (window.requests > properties.webhookRequestsPerMinute()) {
            responseWriter.write(
                    request,
                    response,
                    Math.max(1, 60 - epochSecond % 60)
            );
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute);
        }
        filterChain.doFilter(request, response);
    }

    private record Window(long minute, int requests) {}
}
