package com.translatelab.backend.user.service;

import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.user.avatar.AvatarFormat;
import com.translatelab.backend.user.avatar.AvatarStorageKeyGenerator;
import com.translatelab.backend.user.avatar.AvatarValidator;
import com.translatelab.backend.user.dto.AvatarDownloadResult;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.exception.AvatarNotFoundException;
import com.translatelab.backend.user.exception.InvalidAvatarException;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

@Service
public class AvatarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarService.class);

    private final UserProfileRepository userProfileRepository;
    private final AvatarValidator avatarValidator;
    private final AvatarStorageKeyGenerator avatarStorageKeyGenerator;
    private final StorageService storageService;

    public AvatarService(
            UserProfileRepository userProfileRepository,
            AvatarValidator avatarValidator,
            AvatarStorageKeyGenerator avatarStorageKeyGenerator,
            StorageService storageService
    ) {
        this.userProfileRepository = userProfileRepository;
        this.avatarValidator = avatarValidator;
        this.avatarStorageKeyGenerator = avatarStorageKeyGenerator;
        this.storageService = storageService;
    }

    public void uploadAvatar(UUID userId, MultipartFile file) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        UserProfile profile = findProfile(userId);
        AvatarFormat format = avatarValidator.validate(file);

        String previousObjectKey = profile.getAvatarObjectKey();
        String newObjectKey = avatarStorageKeyGenerator.generateAvatarKey(userId, format);

        uploadFile(file, newObjectKey, format);
        saveAvatarKey(profile, newObjectKey);
        deleteUnusedAvatarObject(previousObjectKey);
    }

    private UserProfile findProfile(UUID userId) {
        return userProfileRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    public AvatarDownloadResult downloadAvatar(UUID userId) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        UserProfile profile = findProfile(userId);
        String objectKey = profile.getAvatarObjectKey();

        if (objectKey == null) {
            throw new AvatarNotFoundException();
        }

        String contentType = resolveContentType(objectKey);

        InputStream inputStream = storageService.download(objectKey);

        return new AvatarDownloadResult(
                inputStream,
                contentType
        );
    }

    public void deleteAvatar(UUID userId) {
        Objects.requireNonNull(userId, "Идентификатор пользователя не должен быть null");

        UserProfile profile = findProfile(userId);
        String objectKey = profile.getAvatarObjectKey();

        if (objectKey == null) {
            return;
        }

        profile.removeAvatar();
        userProfileRepository.saveAndFlush(profile);

        deleteUnusedAvatarObject(objectKey);
    }

    private void uploadFile(MultipartFile file, String objectKey, AvatarFormat format) {
        boolean uploadCompleted = false;

        try (InputStream inputStream = file.getInputStream()) {
            storageService.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    format.contentType()
            );

            uploadCompleted = true;
        } catch (IOException exception) {
            InvalidAvatarException avatarException = new InvalidAvatarException("Не удалось прочитать файл аватара");

            avatarException.addSuppressed(exception);

            if (uploadCompleted) {
                deleteUploadedFile(objectKey, avatarException);
            }

            throw avatarException;
        }
    }

    private void saveAvatarKey(UserProfile profile, String objectKey) {
        try {
            profile.replaceAvatar(objectKey);
            userProfileRepository.saveAndFlush(profile);
        } catch (RuntimeException exception) {
            deleteUploadedFile(objectKey, exception);

            throw exception;
        }
    }

    private void deleteUploadedFile(String objectKey, RuntimeException originalException) {
        try {
            storageService.delete(objectKey);
        } catch (RuntimeException deleteException) {
            originalException.addSuppressed(deleteException);
        }
    }

    private void deleteUnusedAvatarObject(String objectKey) {
        if (objectKey == null) {
            return;
        }

        try {
            storageService.delete(objectKey);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Не удалось удалить неиспользуемый объект аватара {}",
                    objectKey,
                    exception
            );
        }
    }

    private String resolveContentType(String objectKey) {
        for (AvatarFormat format : AvatarFormat.values()) {
            String expectedSuffix = "." + format.extension();

            if (objectKey.endsWith(expectedSuffix)) {
                return format.contentType();
            }
        }

        throw new StorageException(
                "Не удалось определить формат аватара по внутреннему ключу: " + objectKey,
                new IllegalStateException("Внутренний ключ аватара содержит неподдерживаемое расширение")
        );
    }
}