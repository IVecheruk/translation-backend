package com.translatelab.backend.auth.service;

import com.translatelab.backend.auth.dto.RegisterRequest;
import com.translatelab.backend.auth.dto.RegisterResponse;
import com.translatelab.backend.auth.exception.EmailAlreadyExistsException;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.repository.UserProfileRepository;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileRepository userProfileRepository;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserProfileRepository userProfileRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userProfileRepository = userProfileRepository;
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

        User user = new User(email, passwordHash);
        User savedUser = userRepository.save(user);

        UserProfile profile = new UserProfile(savedUser);
        userProfileRepository.save(profile);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }
}