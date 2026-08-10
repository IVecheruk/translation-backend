package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.dto.RegisterRequest;
import com.translatelab.backend.auth.dto.RegisterResponse;
import com.translatelab.backend.auth.exception.EmailAlreadyExistsException;
import com.translatelab.backend.auth.entity.AccountActionTokenType;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.repository.UserProfileRepository;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileRepository userProfileRepository;
    private final AccountActionTokenIssuer tokenIssuer;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserProfileRepository userProfileRepository,
            AccountActionTokenIssuer tokenIssuer
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userProfileRepository = userProfileRepository;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email() // получаем email из объекта запроса
                .strip()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User savedUser;
        try {
            User user = new User(email, passwordHash);
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(email);
        }

        UserProfile profile = new UserProfile(savedUser);
        userProfileRepository.saveAndFlush(profile);

        tokenIssuer.issue(
                savedUser,
                AccountActionTokenType.EMAIL_VERIFICATION
        );

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }
}
