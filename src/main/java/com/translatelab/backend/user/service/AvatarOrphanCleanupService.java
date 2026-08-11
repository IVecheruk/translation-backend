package com.translatelab.backend.user.service;

import com.translatelab.backend.config.AvatarProperties;
import com.translatelab.backend.storage.dto.StoredObjectInfo;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.user.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AvatarOrphanCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AvatarOrphanCleanupService.class
    );
    private static final String AVATAR_PREFIX = "avatars/";

    private final StorageService storageService;
    private final UserProfileRepository userProfileRepository;
    private final AvatarProperties properties;
    private final Clock clock;

    public AvatarOrphanCleanupService(
            StorageService storageService,
            UserProfileRepository userProfileRepository,
            AvatarProperties properties,
            Clock clock
    ) {
        this.storageService = storageService;
        this.userProfileRepository = userProfileRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.avatar.cleanup-interval:1h}")
    public void cleanup() {
        Instant cutoff = clock.instant().minus(properties.orphanRetention());
        List<StoredObjectInfo> candidates = storageService.listOlderThan(
                AVATAR_PREFIX,
                cutoff,
                properties.cleanupBatchSize()
        );

        int deleted = 0;
        int failed = 0;
        for (StoredObjectInfo candidate : candidates) {
            if (userProfileRepository.existsByAvatarObjectKey(
                    candidate.objectKey()
            )) {
                continue;
            }
            try {
                storageService.delete(candidate.objectKey());
                deleted++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn(
                        "Не удалось удалить orphan-объект аватара",
                        exception
                );
            }
        }

        if (deleted > 0 || failed > 0) {
            LOGGER.info(
                    "Очистка orphan-аватаров завершена: deleted={}, failed={}",
                    deleted,
                    failed
            );
        }
    }
}
