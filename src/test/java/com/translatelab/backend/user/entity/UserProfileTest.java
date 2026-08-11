package com.translatelab.backend.user.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserProfileTest {

    @Test
    void shouldCreateEmptyProfileForUser() {
        User user = createUser();

        UserProfile profile = new UserProfile(user);

        assertAll(
                () -> assertSame(user, profile.getUser()),
                () -> assertNull(profile.getUserId()),
                () -> assertNull(profile.getUsername()),
                () -> assertNull(profile.getDisplayName()),
                () -> assertNull(profile.getNickname()),
                () -> assertNull(profile.getProfession()),
                () -> assertNull(profile.getBio()),
                () -> assertNull(profile.getAvatarObjectKey())
        );
    }

    @Test
    void shouldRejectNullUser() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new UserProfile(null)
        );

        assertEquals(
                "Пользователь не должен быть null",
                exception.getMessage()
        );
    }

    @Test
    void shouldUpdateProfileDetails() {
        UserProfile profile = new UserProfile(createUser());

        profile.updateDetails(
                "translator_21",
                "Иван Петров",
                "VanyaDev",
                "Переводчик",
                "Работаю с технической документацией"
        );

        assertAll(
                () -> assertEquals(
                        "translator_21",
                        profile.getUsername()
                ),
                () -> assertEquals(
                        "Иван Петров",
                        profile.getDisplayName()
                ),
                () -> assertEquals(
                        "VanyaDev",
                        profile.getNickname()
                ),
                () -> assertEquals(
                        "Переводчик",
                        profile.getProfession()
                ),
                () -> assertEquals(
                        "Работаю с технической документацией",
                        profile.getBio()
                )
        );
    }

    @Test
    void shouldReplaceAndRemoveAvatar() {
        UserProfile profile = new UserProfile(createUser());
        String objectKey =
                "avatars/user-id/avatar-id.png";

        profile.replaceAvatar(objectKey);

        assertEquals(
                objectKey,
                profile.getAvatarObjectKey()
        );

        profile.removeAvatar();

        assertNull(profile.getAvatarObjectKey());
    }

    private User createUser() {
        return new User(
                "profile@example.com",
                "encoded-password"
        );
    }
}
