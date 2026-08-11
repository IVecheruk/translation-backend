package com.translatelab.backend.common.web;

import com.translatelab.backend.config.ApiRateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRateLimitFilterTest {

    private final ApiRateLimitFilter filter = new ApiRateLimitFilter(
            new ApiRateLimitProperties(1, 1, 1, 1, 1, 1, 100),
            new RateLimitResponseWriter(new ObjectMapper()),
            Clock.fixed(
                    Instant.parse("2026-08-10T12:00:00Z"),
                    ZoneOffset.UTC
            )
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnStandard429AndIgnoreForwardedAddress() throws Exception {
        execute("/api/auth/login", "192.0.2.10", "198.51.100.1");
        MockHttpServletResponse rejected = execute(
                "/api/auth/login",
                "192.0.2.10",
                "203.0.113.5"
        );

        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
        assertTrue(rejected.getContentAsString().contains(
                "Слишком много запросов"
        ));
        assertTrue(rejected.getContentAsString().contains(
                "correlation_id"
        ));
    }

    @Test
    void shouldUseIndependentPoliciesAndAuthenticatedAccounts()
            throws Exception {
        assertEquals(200, execute(
                "/api/auth/register",
                "192.0.2.20",
                null
        ).getStatus());
        assertEquals(200, execute(
                "/api/auth/login",
                "192.0.2.20",
                null
        ).getStatus());

        authenticate("account-one");
        assertEquals(200, execute(
                "/api/documents/upload",
                "192.0.2.30",
                null
        ).getStatus());
        authenticate("account-two");
        assertEquals(200, execute(
                "/api/documents/upload",
                "192.0.2.30",
                null
        ).getStatus());
    }

    private void authenticate(String account) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(account, null, "ROLE_USER")
        );
    }

    private MockHttpServletResponse execute(
            String path,
            String remoteAddress,
            String forwardedAddress
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddress);
        if (forwardedAddress != null) {
            request.addHeader("X-Forwarded-For", forwardedAddress);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
