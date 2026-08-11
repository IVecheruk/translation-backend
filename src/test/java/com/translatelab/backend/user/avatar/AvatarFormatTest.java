package com.translatelab.backend.user.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AvatarFormatTest {

    @ParameterizedTest
    @CsvSource({
            "JPEG, jpg, image/jpeg",
            "PNG, png, image/png"
    })
    void shouldExposeCanonicalExtensionAndContentType(
            AvatarFormat format,
            String expectedExtension,
            String expectedContentType
    ) {
        assertEquals(expectedExtension, format.extension());
        assertEquals(expectedContentType, format.contentType());
    }

    @Test
    void shouldSupportOnlyJpegAndPng() {
        assertArrayEquals(
                new AvatarFormat[]{
                        AvatarFormat.JPEG,
                        AvatarFormat.PNG
                },
                AvatarFormat.values()
        );
    }
}
