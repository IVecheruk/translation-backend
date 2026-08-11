package com.translatelab.backend.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter =
            new RequestCorrelationFilter();

    @Test
    void shouldPreserveSafeCallerCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdContext.HEADER, "frontend-request_42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(
                "frontend-request_42",
                response.getHeader(CorrelationIdContext.HEADER)
        );
    }

    @Test
    void shouldReplaceUnsafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdContext.HEADER, "bad\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertDoesNotThrow(() -> UUID.fromString(
                response.getHeader(CorrelationIdContext.HEADER)
        ));
    }
}
