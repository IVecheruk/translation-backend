package com.translatelab.backend.auth.security;

import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Non-simple header plus the exact-origin CORS policy protects cookie actions. */
public class RefreshRequestGuardFilter extends OncePerRequestFilter {
    private final RestSecurityErrorHandler errorHandler;

    public RefreshRequestGuardFilter(RestSecurityErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isCookieAction(request);
    }

    public static boolean isCookieAction(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod())
                && ("/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"true".equals(request.getHeader("X-Refresh-Request"))) {
            errorHandler.handle(request, response, new AccessDeniedException("Missing refresh request header"));
            return;
        }
        chain.doFilter(request, response);
    }
}
