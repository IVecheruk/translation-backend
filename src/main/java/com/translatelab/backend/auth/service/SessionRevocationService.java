package com.translatelab.backend.auth.service;

import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SessionRevocationService {

    private final UserRepository userRepository;

    public SessionRevocationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void revokeAll(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(UserNotFoundException::new)
                .revokeSessions();
    }
}
