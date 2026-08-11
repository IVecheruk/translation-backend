package com.translatelab.backend.user.service;

import com.translatelab.backend.user.dto.ProfileResponse;
import com.translatelab.backend.user.dto.UpdateProfileRequest;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.entity.UserProfile;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "647b83b0-3277-439f-9248-6943f0afe433"
    );
    private static final Instant ACCOUNT_CREATED_AT =
            Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant PROFILE_UPDATED_AT =
            Instant.parse("2026-07-20T12:30:00Z");

    @Mock
    private UserProfileRepository userProfileRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userProfileRepository);
    }

    @Test
    void shouldReturnCompletePrivateProfile() {
        UserProfile profile = createProfile();
        given(userProfileRepository.findById(USER_ID))
                .willReturn(Optional.of(profile));

        ProfileResponse response = profileService.getProfile(USER_ID);

        assertAll(
                () -> assertEquals(USER_ID, response.userId()),
                () -> assertEquals(
                        "user@example.com",
                        response.email()
                ),
                () -> assertEquals("Ivan", response.username()),
                () -> assertEquals(
                        "Иван Иванов",
                        response.displayName()
                ),
                () -> assertEquals("Vanya", response.nickname()),
                () -> assertEquals(
                        "Переводчик",
                        response.profession()
                ),
                () -> assertEquals(
                        "Работаю с техническими текстами",
                        response.bio()
                ),
                () -> assertTrue(response.hasAvatar()),
                () -> assertEquals(
                        ACCOUNT_CREATED_AT,
                        response.createdAt()
                ),
                () -> assertEquals(
                        PROFILE_UPDATED_AT,
                        response.updatedAt()
                )
        );

        verify(userProfileRepository).findById(USER_ID);
    }

    @Test
    void shouldUpdateProfileAndReturnFlushedState() {
        UserProfile profile = createProfile();
        Instant updatedAfterFlush =
                Instant.parse("2026-07-28T08:00:00Z");
        UpdateProfileRequest request = new UpdateProfileRequest(
                "NewUser",
                "Новое имя",
                null,
                "Разработчик",
                "Новое описание"
        );

        given(userProfileRepository.findByIdForUpdate(USER_ID))
                .willReturn(Optional.of(profile));
        given(userProfileRepository.saveAndFlush(profile))
                .willAnswer(invocation -> {
                    ReflectionTestUtils.setField(
                            profile,
                            "updatedAt",
                            updatedAfterFlush
                    );
                    return profile;
                });

        ProfileResponse response = profileService.updateProfile(
                USER_ID,
                request
        );

        assertAll(
                () -> assertEquals("NewUser", profile.getUsername()),
                () -> assertEquals(
                        "Новое имя",
                        profile.getDisplayName()
                ),
                () -> assertNull(profile.getNickname()),
                () -> assertEquals(
                        "Разработчик",
                        profile.getProfession()
                ),
                () -> assertEquals(
                        "Новое описание",
                        profile.getBio()
                ),
                () -> assertEquals("NewUser", response.username()),
                () -> assertEquals(
                        updatedAfterFlush,
                        response.updatedAt()
                )
        );

        verify(userProfileRepository).findByIdForUpdate(USER_ID);
        verify(userProfileRepository).saveAndFlush(profile);
    }

    @Test
    void shouldThrowWhenProfileDoesNotExist() {
        given(userProfileRepository.findById(USER_ID))
                .willReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> profileService.getProfile(USER_ID)
        );

        verify(userProfileRepository).findById(USER_ID);
    }

    @Test
    void shouldRejectNullUserIdWhenReadingProfile() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> profileService.getProfile(null)
        );

        assertEquals(
                "Идентификатор пользователя не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void shouldRejectNullUserIdWhenUpdatingProfile() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                null,
                null,
                null,
                null
        );

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> profileService.updateProfile(null, request)
        );

        assertEquals(
                "Идентификатор пользователя не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void shouldRejectNullUpdateRequest() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> profileService.updateProfile(USER_ID, null)
        );

        assertEquals(
                "Запрос на обновление не должен быть null",
                exception.getMessage()
        );
        verifyNoInteractions(userProfileRepository);
    }

    private UserProfile createProfile() {
        User user = new User(
                "user@example.com",
                "encoded-password"
        );
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(
                user,
                "createdAt",
                ACCOUNT_CREATED_AT
        );

        UserProfile profile = new UserProfile(user);
        ReflectionTestUtils.setField(profile, "userId", USER_ID);
        ReflectionTestUtils.setField(
                profile,
                "updatedAt",
                PROFILE_UPDATED_AT
        );
        profile.updateDetails(
                "Ivan",
                "Иван Иванов",
                "Vanya",
                "Переводчик",
                "Работаю с техническими текстами"
        );
        profile.replaceAvatar(
                "avatars/647b83b0-3277-439f-9248-6943f0afe433/avatar.png"
        );

        return profile;
    }
}
