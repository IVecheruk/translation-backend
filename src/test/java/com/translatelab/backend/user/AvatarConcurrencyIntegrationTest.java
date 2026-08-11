package com.translatelab.backend.user;

import com.translatelab.backend.storage.dto.StoredObjectInfo;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.repository.UserProfileRepository;
import com.translatelab.backend.user.repository.UserRepository;
import com.translatelab.backend.user.service.AvatarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AvatarConcurrencyIntegrationTest {

    @Autowired private AvatarService avatarService;
    @Autowired private StorageService storageService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository profileRepository;

    private UUID userId;

    @AfterEach
    void cleanUp() {
        if (userId == null) {
            return;
        }
        List<StoredObjectInfo> objects = objects();
        for (StoredObjectInfo object : objects) {
            storageService.delete(object.objectKey());
        }
        profileRepository.deleteById(userId);
        userRepository.deleteById(userId);
    }

    @Test
    void concurrentReplacementAndDeleteMustLeaveConsistentKeyAndObjects()
            throws Exception {
        createProfile();
        avatarService.uploadAvatar(
                userId,
                imageFile("png", "image/png", Color.BLUE)
        );

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> replacement = executor.submit(() -> {
                await(start);
                avatarService.uploadAvatar(
                        userId,
                        imageFile("jpeg", "image/jpeg", Color.GREEN)
                );
            });
            Future<?> deletion = executor.submit(() -> {
                await(start);
                avatarService.deleteAvatar(userId);
            });

            start.countDown();
            replacement.get();
            deletion.get();
        }

        UserProfile finalProfile = profileRepository.findById(userId)
                .orElseThrow();
        List<StoredObjectInfo> objects = objects();
        String finalKey = finalProfile.getAvatarObjectKey();

        if (finalKey == null) {
            assertTrue(objects.isEmpty());
        } else {
            assertEquals(1, objects.size());
            assertEquals(finalKey, objects.getFirst().objectKey());
            try (var input = storageService.download(finalKey)) {
                assertTrue(input.readAllBytes().length > 0);
            }
        }
    }

    @Test
    void concurrentDeletesMustBeIdempotent() throws Exception {
        createProfile();
        avatarService.uploadAvatar(
                userId,
                imageFile("png", "image/png", Color.ORANGE)
        );

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> {
                await(start);
                avatarService.deleteAvatar(userId);
            });
            Future<?> second = executor.submit(() -> {
                await(start);
                avatarService.deleteAvatar(userId);
            });
            start.countDown();
            first.get();
            second.get();
        }

        assertNull(profileRepository.findById(userId)
                .orElseThrow()
                .getAvatarObjectKey());
        assertTrue(objects().isEmpty());
    }

    private void createProfile() {
        User user = userRepository.saveAndFlush(new User(
                "avatar-stage6-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        ));
        userId = user.getId();
        profileRepository.saveAndFlush(new UserProfile(user));
    }

    private List<StoredObjectInfo> objects() {
        return storageService.listOlderThan(
                "avatars/" + userId + "/",
                Instant.now().plusSeconds(60),
                100
        );
    }

    private MockMultipartFile imageFile(
            String format,
            String contentType,
            Color color
    ) {
        try {
            BufferedImage image = new BufferedImage(
                    32,
                    32,
                    BufferedImage.TYPE_INT_RGB
            );
            var graphics = image.createGraphics();
            graphics.setColor(color);
            graphics.fillRect(0, 0, 32, 32);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, format, output);
            return new MockMultipartFile(
                    "file",
                    "avatar." + format,
                    contentType,
                    output.toByteArray()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
