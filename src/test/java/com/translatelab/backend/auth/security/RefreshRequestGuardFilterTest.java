package com.translatelab.backend.auth.security;

import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RefreshRequestGuardFilterTest {
    private final RefreshRequestGuardFilter filter = new RefreshRequestGuardFilter(
            new RestSecurityErrorHandler(new ObjectMapper())
    );

    @Test
    void protectsCookieActionsWithContextPathAndRejectsWrongHeader() throws Exception {
        for (String action : new String[]{"refresh", "logout"}) {
            for (String value : new String[]{"", "false", "TRUE"}) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/backend/api/auth/" + action);
                request.setContextPath("/backend");
                request.addHeader("X-Refresh-Request", value);
                MockHttpServletResponse response = new MockHttpServletResponse();
                MockFilterChain chain = new MockFilterChain();
                filter.doFilter(request, response, chain);
                assertEquals(403, response.getStatus());
                assertNull(chain.getRequest());
            }
        }
    }

    @Test
    void letsProtectedActionThroughWithRequiredHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.addHeader("X-Refresh-Request", "true");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }
}
