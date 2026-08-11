package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.dto.LoginRequest;
import com.translatelab.backend.auth.dto.LoginResponse;
import com.translatelab.backend.auth.exception.InvalidCredentialsException;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private LoginService loginService;

    @Test
    void shouldNormalizeEmailVerifyPasswordAndReturnToken() {
        LoginRequest request = new LoginRequest(
                "  User@Example.COM  ",
                "password123"
        );
        User user = new User(
                "user@example.com",
                "hashed-password"
        );

        given(userRepository.findByEmail("user@example.com"))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(
                "password123",
                "hashed-password"
        )).willReturn(true);
        given(jwtService.generateAccessToken(user))
                .willReturn("test-access-token");
        given(jwtService.getAccessTokenTtlSeconds())
                .willReturn(3600L);

        LoginResponse response = loginService.login(request);

        assertEquals("test-access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());

        verify(userRepository).findByEmail("user@example.com");
        verify(passwordEncoder).matches(
                "password123",
                "hashed-password"
        );
        verify(jwtService).generateAccessToken(user);
        verify(jwtService).getAccessTokenTtlSeconds();
    }

    @Test
    void shouldThrowWhenUserDoesNotExist() {
        LoginRequest request = new LoginRequest(
                "  Missing@Example.COM  ",
                "password123"
        );

        given(userRepository.findByEmail("missing@example.com"))
                .willReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.login(request)
        );

        assertEquals(
                "Неверный email или пароль",
                exception.getMessage()
        );
        verify(userRepository).findByEmail("missing@example.com");
        verify(passwordEncoder).matches(eq("password123"), anyString());
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldThrowWhenPasswordIsInvalid() {
        LoginRequest request = new LoginRequest(
                "user@example.com",
                "wrong-password"
        );
        User user = new User(
                "user@example.com",
                "hashed-password"
        );

        given(userRepository.findByEmail("user@example.com"))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(
                "wrong-password",
                "hashed-password"
        )).willReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> loginService.login(request)
        );

        assertEquals(
                "Неверный email или пароль",
                exception.getMessage()
        );
        verify(passwordEncoder).matches(
                "wrong-password",
                "hashed-password"
        );
        verifyNoInteractions(jwtService);
    }
}
