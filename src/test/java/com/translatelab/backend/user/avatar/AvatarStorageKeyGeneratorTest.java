package com.translatelab.backend.user.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarStorageKeyGeneratorTest {

    private static final UUID USER_ID = UUID.fromString(
            "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
    );

    private final AvatarStorageKeyGenerator keyGenerator =
            new AvatarStorageKeyGenerator();

    @ParameterizedTest
    @EnumSource(AvatarFormat.class)
    void shouldGenerateValidAvatarKey(AvatarFormat format) {
        String key = keyGenerator.generateAvatarKey(
                USER_ID,
                format
        );

        String prefix = "avatars/" + USER_ID + "/";
        String suffix = "." + format.extension();

        assertTrue(key.startsWith(prefix));
        assertTrue(key.endsWith(suffix));

        String objectId = key.substring(
                prefix.length(),
                key.length() - suffix.length()
        );

        assertDoesNotThrow(() -> UUID.fromString(objectId));
    }

    @Test
    void shouldGenerateUniqueKeysForSameUserAndFormat() {
        String firstKey = keyGenerator.generateAvatarKey(
                USER_ID,
                AvatarFormat.JPEG
        );
        String secondKey = keyGenerator.generateAvatarKey(
                USER_ID,
                AvatarFormat.JPEG
        );

        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void shouldRejectNullUserId() {
        assertThrows(
                NullPointerException.class,
                () -> keyGenerator.generateAvatarKey(
                        null,
                        AvatarFormat.JPEG
                )
        );
    }

    @Test
    void shouldRejectNullAvatarFormat() {
        assertThrows(
                NullPointerException.class,
                () -> keyGenerator.generateAvatarKey(
                        USER_ID,
                        null
                )
        );
    }
}
