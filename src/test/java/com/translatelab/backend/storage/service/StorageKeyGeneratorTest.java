package com.translatelab.backend.storage.service;

import com.translatelab.backend.translation.entity.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageKeyGeneratorTest {

    private static final UUID USER_ID = UUID.fromString(
            "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
    );

    private final StorageKeyGenerator keyGenerator =
            new StorageKeyGenerator();

    @ParameterizedTest
    @EnumSource(FileFormat.class)
    void shouldGenerateValidSourceFileKey(FileFormat fileFormat) {
        String key = keyGenerator.generateSourceFileKey(
                USER_ID,
                fileFormat
        );

        String prefix = "uploads/" + USER_ID + "/";
        String suffix = "." + fileFormat
                .name()
                .toLowerCase(Locale.ROOT);

        assertTrue(key.startsWith(prefix));
        assertTrue(key.endsWith(suffix));

        String objectId = key.substring(
                prefix.length(),
                key.length() - suffix.length()
        );

        assertDoesNotThrow(() -> UUID.fromString(objectId));
    }

    @ParameterizedTest
    @EnumSource(FileFormat.class)
    void shouldGenerateResultKeyInsideUserNamespace(
            FileFormat fileFormat
    ) {
        String key = keyGenerator.generateResultFileKey(
                USER_ID,
                fileFormat
        );

        String prefix = "results/" + USER_ID + "/";
        String suffix = "." + fileFormat.jsonValue();
        assertTrue(key.startsWith(prefix));
        assertTrue(key.endsWith(suffix));
        assertDoesNotThrow(() -> UUID.fromString(key.substring(
                prefix.length(),
                key.length() - suffix.length()
        )));
    }

    @Test
    void shouldGenerateUniqueResultKeys() {
        assertNotEquals(
                keyGenerator.generateResultFileKey(USER_ID, FileFormat.PDF),
                keyGenerator.generateResultFileKey(USER_ID, FileFormat.PDF)
        );
    }

    @Test
    void shouldGenerateUniqueKeysForSameUserAndFormat() {
        String firstKey = keyGenerator.generateSourceFileKey(
                USER_ID,
                FileFormat.PDF
        );
        String secondKey = keyGenerator.generateSourceFileKey(
                USER_ID,
                FileFormat.PDF
        );

        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void shouldRejectNullUserId() {
        assertThrows(
                NullPointerException.class,
                () -> keyGenerator.generateSourceFileKey(
                        null,
                        FileFormat.PDF
                )
        );
    }

    @Test
    void shouldRejectNullFileFormat() {
        assertThrows(
                NullPointerException.class,
                () -> keyGenerator.generateSourceFileKey(
                        USER_ID,
                        null
                )
        );
    }
}
