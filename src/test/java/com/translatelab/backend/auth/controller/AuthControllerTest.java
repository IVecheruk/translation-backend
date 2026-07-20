package com.translatelab.backend.auth.controller;

import com.translatelab.backend.auth.dto.RegisterRequest;
import com.translatelab.backend.auth.dto.RegisterResponse;
import com.translatelab.backend.auth.exception.EmailAlreadyExistsException;
import com.translatelab.backend.auth.service.RegistrationService;
import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static
        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

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
}