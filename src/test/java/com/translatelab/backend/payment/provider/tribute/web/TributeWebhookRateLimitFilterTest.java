package com.translatelab.backend.payment.provider.tribute.web;

import com.translatelab.backend.config.TributeProperties;
import com.translatelab.backend.common.web.RateLimitResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TributeWebhookRateLimitFilterTest {

    @Test
    void shouldRejectFloodWithoutInvokingDownstreamChain() throws Exception {
        TributeProperties properties = new TributeProperties(
                true,
                URI.create("https://tribute.tg/api/v1"),
                "synthetic-key",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                65_536,
                1
        );
        TributeWebhookRateLimitFilter filter = new TributeWebhookRateLimitFilter(
                properties,
                Clock.fixed(
                        Instant.parse("2026-08-08T12:00:00Z"),
                        ZoneOffset.UTC
                ),
                new RateLimitResponseWriter(
                        new tools.jackson.databind.ObjectMapper()
                )
        );

        MockHttpServletRequest first = request();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());

        MockHttpServletRequest second = request();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, secondResponse.getStatus());
        assertEquals("60", secondResponse.getHeader("Retry-After"));
        assertEquals(
                "application/json;charset=UTF-8",
                secondResponse.getContentType()
        );
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/payments/webhooks/tribute"
        );
        request.setRemoteAddr("192.0.2.1");
        return request;
    }
}
