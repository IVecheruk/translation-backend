package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.dto.LoginRequest;
import com.translatelab.backend.auth.dto.SessionTokens;
import com.translatelab.backend.auth.exception.InvalidCredentialsException;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class LoginService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20zMPSb8ukP1vN2bBJgaFyATqyo54fO";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public SessionTokens login(LoginRequest request) {
        String email = request.email()
                .strip()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailForUpdate(email).orElse(null);

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash()
        );
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        return refreshTokenService.open(user);
    }
}
