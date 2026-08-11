package com.translatelab.backend.user.service;

import com.translatelab.backend.config.AvatarProperties;
import com.translatelab.backend.storage.dto.StoredObjectInfo;
import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AvatarOrphanCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final String REFERENCED = "avatars/user/current.png";
    private static final String ORPHAN = "avatars/user/orphan.jpg";
    private static final String FAILED = "avatars/user/failed.png";

    @Mock private StorageService storageService;
    @Mock private UserProfileRepository repository;

    private AvatarOrphanCleanupService service;

    @BeforeEach
    void setUp() {
        AvatarProperties properties = new AvatarProperties(
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(2),
                DataSize.ofMegabytes(64),
                4096,
                4096,
                16_777_216,
                Duration.ofHours(1),
                Duration.ofHours(1),
                2
        );
        service = new AvatarOrphanCleanupService(
                storageService,
                repository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldDeleteOnlyUnreferencedOldObjectsInBoundedBatch() {
        Instant cutoff = NOW.minus(Duration.ofHours(1));
        given(storageService.listOlderThan("avatars/", cutoff, 2))
                .willReturn(List.of(
                        new StoredObjectInfo(REFERENCED, cutoff),
                        new StoredObjectInfo(ORPHAN, cutoff)
                ));
        given(repository.existsByAvatarObjectKey(REFERENCED))
                .willReturn(true);
        given(repository.existsByAvatarObjectKey(ORPHAN))
                .willReturn(false);

        service.cleanup();

        verify(storageService, never()).delete(REFERENCED);
        verify(storageService).delete(ORPHAN);
    }

    @Test
    void shouldContinueWhenSingleOrphanDeletionFails() {
        Instant cutoff = NOW.minus(Duration.ofHours(1));
        given(storageService.listOlderThan("avatars/", cutoff, 2))
                .willReturn(List.of(
                        new StoredObjectInfo(FAILED, cutoff),
                        new StoredObjectInfo(ORPHAN, cutoff)
                ));
        doThrow(new StorageException("failure", new RuntimeException()))
                .when(storageService).delete(FAILED);

        assertDoesNotThrow(service::cleanup);

        verify(storageService).delete(FAILED);
        verify(storageService).delete(ORPHAN);
    }
}
