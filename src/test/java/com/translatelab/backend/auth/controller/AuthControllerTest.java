package com.translatelab.backend.auth.controller;

import com.translatelab.backend.auth.dto.LoginRequest;
import com.translatelab.backend.auth.dto.LoginResponse;
import com.translatelab.backend.auth.dto.RegisterRequest;
import com.translatelab.backend.auth.dto.RegisterResponse;
import com.translatelab.backend.auth.exception.EmailAlreadyExistsException;
import com.translatelab.backend.auth.exception.InvalidCredentialsException;
import com.translatelab.backend.auth.service.LoginService;
import com.translatelab.backend.auth.service.RegistrationService;
import com.translatelab.backend.auth.service.AccountRecoveryService;
import com.translatelab.backend.auth.service.SessionRevocationService;
import com.translatelab.backend.auth.security.LoginAttemptLimiter;
import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private AccountRecoveryService accountRecoveryService;

    @MockitoBean
    private SessionRevocationService sessionRevocationService;

    @MockitoBean
    private LoginAttemptLimiter loginAttemptLimiter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldRegisterUser() throws Exception {
        UUID userId = UUID.fromString(
                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
        );
        Instant createdAt = Instant.parse("2026-07-20T10:00:00Z");

        RegisterResponse response = new RegisterResponse(
                userId,
                "user@example.com",
                createdAt
        );

        given(registrationService.register(any(RegisterRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "email": "user@example.com",
                                    "password": "password123"
                                  }
                                  """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.createdAt")
                        .value(createdAt.toString()));

        verify(registrationService).register(
                new RegisterRequest(
                        "user@example.com",
                        "password123"
                )
        );
    }

    @Test
    void shouldReturnBadRequestWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "email": "incorrect-email",
                                    "password": "123"
                                  }
                                  """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Ошибка валидации запроса"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());

        verifyNoInteractions(registrationService);
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        given(registrationService.register(any(RegisterRequest.class)))
                .willThrow(
                        new EmailAlreadyExistsException(
                                "user@example.com"
                        )
                );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "email": "user@example.com",
                                    "password": "password123"
                                  }
                                  """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Пользователь с email: user@example.com уже существует"
                ))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void shouldReturnBadRequestWhenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "email": "user@example.com",
                                    "password":
                                  }
                                  """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Некорректное тело запроса"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(registrationService);
    }

    @Test
    void shouldLoginUser() throws Exception {
        LoginResponse response = new LoginResponse(
                "test-access-token",
                "Bearer",
                3600
        );

        given(loginService.login(any(LoginRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "email": "user@example.com",
                                    "password": "password123"
                                  }
                                  """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("test-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        verify(loginService).login(
                new LoginRequest(
                        "user@example.com",
                        "password123"
                )
        );
        verify(loginAttemptLimiter).check(
                "user@example.com",
                "127.0.0.1"
        );
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        given(loginService.login(any(LoginRequest.class)))
                .willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "email": "user@example.com",
                                    "password": "wrong-password"
                                  }
                                  """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Неверный email или пароль"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/login"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void shouldReturnBadRequestWhenLoginRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "email": "incorrect-email",
                                    "password": ""
                                  }
                                  """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Ошибка валидации запроса"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/login"))
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());

        verifyNoInteractions(loginService);
    }

    @Test
    void shouldReturnUnauthorizedWhenAccessTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/documents/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/history"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void shouldReturnUnauthorizedWhenAccessTokenIsInvalid() throws Exception {
        given(jwtDecoder.decode("invalid-token"))
                .willThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(get("/api/documents/history")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/history"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(jwtDecoder).decode("invalid-token");
    }

    @Test
    void shouldReturnGenericAcceptedForPasswordResetRequest() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com"}
                                """))
                .andExpect(status().isAccepted());

        verify(accountRecoveryService)
                .requestPasswordReset("unknown@example.com");
    }

    @Test
    void shouldConfirmEmailWithoutReturningTokenData() throws Exception {
        String token = "0123456789012345678901234567890123456789012";

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());

        verify(accountRecoveryService).confirmEmail(token);
    }

    @Test
    void shouldResetPasswordWithoutReturningSensitiveData() throws Exception {
        String token = "0123456789012345678901234567890123456789012";

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token":"%s",
                                  "password":"new-password"
                                }
                                """.formatted(token)))
                .andExpect(status().isNoContent());

        verify(accountRecoveryService)
                .resetPassword(token, "new-password");
    }

    @Test
    void shouldRevokeAllSessionsForAuthenticatedAccount() throws Exception {
        UUID userId = UUID.fromString(
                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
        );

        mockMvc.perform(post("/api/auth/logout-all")
                        .with(jwt().jwt(token -> token.subject(
                                userId.toString()
                        ))))
                .andExpect(status().isNoContent());

        verify(sessionRevocationService).revokeAll(userId);
    }
}
