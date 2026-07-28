package com.translatelab.backend.user.service;

import com.translatelab.backend.user.dto.ProfileResponse;
import com.translatelab.backend.user.dto.UpdateProfileRequest;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class ProfileService {

    private final UserProfileRepository userProfileRepository;

    public ProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        UserProfile profile = findProfile(userId);

        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        Objects.requireNonNull(
                request,
                "Запрос на обновление не должен быть null"
        );

        UserProfile profile = findProfile(userId);

        profile.updateDetails(
                request.username(),
                request.displayName(),
                request.nickname(),
                request.profession(),
                request.bio()
        );

        UserProfile savedProfile = userProfileRepository.saveAndFlush(profile);

        return toResponse(savedProfile);
    }

    private UserProfile findProfile(UUID userId) {
        return userProfileRepository
                .findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private ProfileResponse toResponse(UserProfile profile) {
        User user = profile.getUser();

        return new ProfileResponse(
                profile.getUserId(),
                user.getEmail(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getNickname(),
                profile.getProfession(),
                profile.getBio(),
                profile.getAvatarObjectKey() != null,
                user.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}