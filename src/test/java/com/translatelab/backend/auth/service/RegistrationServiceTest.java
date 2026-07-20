package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.dto.RegisterRequest;
import com.translatelab.backend.auth.dto.RegisterResponse;
import com.translatelab.backend.auth.exception.EmailAlreadyExistsException;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void shouldNormalizeEmailHashPasswordAndReturnSavedUser() {
        RegisterRequest request = new RegisterRequest(
                "  User@Example.COM  ",
                "password123"
        );

        UUID userId = UUID.fromString(
                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
        );
        Instant createdAt = Instant.parse("2026-07-20T10:00:00Z");

        User savedUser = org.mockito.Mockito.mock(User.class);

        given(userRepository.existsByEmail("user@example.com"))
                .willReturn(false);
        given(passwordEncoder.encode("password123"))
                .willReturn("hashed-password");
        given(userRepository.save(any(User.class)))
                .willReturn(savedUser);

        given(savedUser.getId()).willReturn(userId);
        given(savedUser.getEmail()).willReturn("user@example.com");
        given(savedUser.getCreatedAt()).willReturn(createdAt);

        RegisterResponse response = registrationService.register(request);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .existsByEmail("user@example.com");
        verify(passwordEncoder)
                .encode("password123");
        verify(userRepository)
                .save(userCaptor.capture());

        User userPassedToRepository = userCaptor.getValue();

        assertEquals(
                "user@example.com",
                userPassedToRepository.getEmail()
        );
        assertEquals(
                "hashed-password",
                userPassedToRepository.getPasswordHash()
        );

        assertEquals(userId, response.id());
        assertEquals("user@example.com", response.email());
        assertEquals(createdAt, response.createdAt());
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "  User@Example.COM  ",
                "password123"
        );

        given(userRepository.existsByEmail("user@example.com"))
                .willReturn(true);

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> registrationService.register(request)
        );

        assertEquals(
                "Пользователь с email: user@example.com уже существует",
                exception.getMessage()
        );

        verify(userRepository)
                .existsByEmail("user@example.com");
        verify(userRepository, never())
                .save(any(User.class));
        verifyNoInteractions(passwordEncoder);
        verifyNoMoreInteractions(userRepository);
    }
}