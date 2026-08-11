package com.translatelab.backend.user.service;

import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.user.avatar.AvatarFormat;
import com.translatelab.backend.user.avatar.AvatarStorageKeyGenerator;
import com.translatelab.backend.user.avatar.AvatarValidator;
import com.translatelab.backend.user.avatar.ValidatedAvatar;
import com.translatelab.backend.user.dto.AvatarDownloadResult;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.exception.AvatarNotFoundException;
import com.translatelab.backend.user.exception.InvalidAvatarException;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
    );
    private static final String NEW_KEY =
            "avatars/" + USER_ID + "/new.jpg";
    private static final String OLD_KEY =
            "avatars/" + USER_ID + "/old.png";
    private static final byte[] NORMALIZED = {1, 2, 3, 4, 5};

    @Mock private UserProfileRepository repository;
    @Mock private AvatarValidator validator;
    @Mock private AvatarStorageKeyGenerator keyGenerator;
    @Mock private StorageService storageService;
    @Mock private MultipartFile file;
    @Mock private UserProfile profile;

    private AvatarService service;

    @BeforeEach
    void setUp() {
        service = new AvatarService(
                repository,
                validator,
                keyGenerator,
                storageService
        );
    }

    @Test
    void shouldStoreOnlyNormalizedAvatarAndPersistKey() throws Exception {
        prepareUpload(null);

        service.uploadAvatar(USER_ID, file);

        ArgumentCaptor<InputStream> stream = ArgumentCaptor.forClass(
                InputStream.class
        );
        verify(storageService).upload(
                eq(NEW_KEY),
                stream.capture(),
                eq((long) NORMALIZED.length),
                eq("image/jpeg")
        );
        assertArrayEquals(NORMALIZED, stream.getValue().readAllBytes());
        verify(profile).replaceAvatar(NEW_KEY);
        verify(repository).saveAndFlush(profile);
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void shouldLockProfileAndDeleteOldObjectAfterDatabaseFlush() {
        prepareUpload(OLD_KEY);

        service.uploadAvatar(USER_ID, file);

        InOrder order = inOrder(repository, storageService, profile);
        order.verify(repository).findByIdForUpdate(USER_ID);
        order.verify(storageService).upload(
                eq(NEW_KEY), any(InputStream.class),
                eq((long) NORMALIZED.length), eq("image/jpeg")
        );
        order.verify(profile).replaceAvatar(NEW_KEY);
        order.verify(repository).saveAndFlush(profile);
        order.verify(storageService).delete(OLD_KEY);
    }

    @Test
    void shouldStopBeforeLockWhenValidationFails() {
        InvalidAvatarException failure = new InvalidAvatarException("invalid");
        when(validator.validateAndNormalize(file)).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        InvalidAvatarException.class,
                        () -> service.uploadAvatar(USER_ID, file)
                )
        );
        verifyNoInteractions(repository, keyGenerator, storageService, profile);
    }

    @Test
    void shouldRejectMissingProfileBeforeStorageWrite() {
        when(validator.validateAndNormalize(file)).thenReturn(validated());
        when(repository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> service.uploadAvatar(USER_ID, file)
        );
        verifyNoInteractions(keyGenerator, storageService, profile);
    }

    @Test
    void shouldCompensateUploadedObjectWhenDatabaseFlushFails() {
        prepareUpload(OLD_KEY);
        RuntimeException failure = new RuntimeException("database unavailable");
        when(repository.saveAndFlush(profile)).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        RuntimeException.class,
                        () -> service.uploadAvatar(USER_ID, file)
                )
        );
        verify(storageService).delete(NEW_KEY);
        verify(storageService, never()).delete(OLD_KEY);
    }

    @Test
    void shouldKeepSuccessfulReplacementWhenOldCleanupFails() {
        prepareUpload(OLD_KEY);
        doThrow(new StorageException(
                "cleanup failed",
                new RuntimeException()
        )).when(storageService).delete(OLD_KEY);

        assertDoesNotThrow(() -> service.uploadAvatar(USER_ID, file));

        verify(repository).saveAndFlush(profile);
    }

    @Test
    void shouldDownloadAvatarWithCanonicalContentType() {
        InputStream content = new ByteArrayInputStream(NORMALIZED);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(profile.getAvatarObjectKey()).thenReturn(OLD_KEY);
        when(storageService.download(OLD_KEY)).thenReturn(content);

        AvatarDownloadResult result = service.downloadAvatar(USER_ID);

        assertSame(content, result.inputStream());
        assertEquals("image/png", result.contentType());
    }

    @Test
    void shouldRejectMissingAvatarOnDownload() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(profile.getAvatarObjectKey()).thenReturn(null);

        assertThrows(
                AvatarNotFoundException.class,
                () -> service.downloadAvatar(USER_ID)
        );
        verifyNoInteractions(storageService);
    }

    @Test
    void shouldLockClearAndThenDeleteAvatar() {
        when(repository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(profile));
        when(profile.getAvatarObjectKey()).thenReturn(OLD_KEY);

        service.deleteAvatar(USER_ID);

        InOrder order = inOrder(repository, profile, storageService);
        order.verify(repository).findByIdForUpdate(USER_ID);
        order.verify(profile).removeAvatar();
        order.verify(repository).saveAndFlush(profile);
        order.verify(storageService).delete(OLD_KEY);
    }

    @Test
    void shouldTreatMissingAvatarAsAlreadyDeleted() {
        when(repository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(profile));
        when(profile.getAvatarObjectKey()).thenReturn(null);

        service.deleteAvatar(USER_ID);

        verify(profile, never()).removeAvatar();
        verify(repository, never()).saveAndFlush(profile);
        verifyNoInteractions(storageService);
    }

    @Test
    void shouldNotDeleteObjectWhenDatabaseClearFails() {
        when(repository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(profile));
        when(profile.getAvatarObjectKey()).thenReturn(OLD_KEY);
        when(repository.saveAndFlush(profile))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThrows(
                RuntimeException.class,
                () -> service.deleteAvatar(USER_ID)
        );
        verify(storageService, never()).delete(OLD_KEY);
    }

    @Test
    void shouldRejectNullUserIdBeforeDependencies() {
        assertThrows(
                NullPointerException.class,
                () -> service.uploadAvatar(null, file)
        );
        verifyNoInteractions(repository, validator, keyGenerator, storageService);
    }

    private void prepareUpload(String oldKey) {
        when(validator.validateAndNormalize(file)).thenReturn(validated());
        when(repository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(profile));
        when(profile.getAvatarObjectKey()).thenReturn(oldKey);
        when(keyGenerator.generateAvatarKey(USER_ID, AvatarFormat.JPEG))
                .thenReturn(NEW_KEY);
    }

    private ValidatedAvatar validated() {
        return new ValidatedAvatar(AvatarFormat.JPEG, NORMALIZED);
    }
}
