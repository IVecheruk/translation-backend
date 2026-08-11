package com.translatelab.backend.user.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.user.dto.ProfileResponse;
import com.translatelab.backend.user.dto.UpdateProfileRequest;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class ProfileControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "647b83b0-3277-439f-9248-6943f0afe433"
    );
    private static final String TOKEN = "valid-token";
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant UPDATED_AT =
            Instant.parse("2026-07-28T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnAuthenticatedUsersProfile() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(profileService.getProfile(USER_ID))
                .willReturn(createResponse());

        mockMvc.perform(get("/api/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.user_id")
                        .value(USER_ID.toString()))
                .andExpect(jsonPath("$.email")
                        .value("user@example.com"))
                .andExpect(jsonPath("$.username").value("Ivan"))
                .andExpect(jsonPath("$.display_name")
                        .value("Иван Иванов"))
                .andExpect(jsonPath("$.nickname").value("Vanya"))
                .andExpect(jsonPath("$.profession")
                        .value("Переводчик"))
                .andExpect(jsonPath("$.bio")
                        .value("Описание профиля"))
                .andExpect(jsonPath("$.has_avatar").value(true))
                .andExpect(jsonPath("$.created_at")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updated_at")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.avatar_object_key")
                        .doesNotExist());

        verify(jwtDecoder).decode(TOKEN);
        verify(profileService).getProfile(USER_ID);
    }

    @Test
    void shouldUpdateAuthenticatedUsersProfile() throws Exception {
        UpdateProfileRequest expectedRequest =
                new UpdateProfileRequest(
                        "NewUser",
                        "Новое имя",
                        null,
                        "Разработчик",
                        "Новое описание"
                );
        ProfileResponse response = new ProfileResponse(
                USER_ID,
                "user@example.com",
                "NewUser",
                "Новое имя",
                null,
                "Разработчик",
                "Новое описание",
                true,
                CREATED_AT,
                UPDATED_AT
        );
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(profileService.updateProfile(
                USER_ID,
                expectedRequest
        )).willReturn(response);

        mockMvc.perform(put("/api/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "  NewUser  ",
                                  "display_name": "  Новое имя  ",
                                  "nickname": "   ",
                                  "profession": "Разработчик",
                                  "bio": "Новое описание"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id")
                        .value(USER_ID.toString()))
                .andExpect(jsonPath("$.username")
                        .value("NewUser"))
                .andExpect(jsonPath("$.display_name")
                        .value("Новое имя"))
                .andExpect(jsonPath("$.nickname").isEmpty())
                .andExpect(jsonPath("$.profession")
                        .value("Разработчик"))
                .andExpect(jsonPath("$.bio")
                        .value("Новое описание"));

        verify(jwtDecoder).decode(TOKEN);
        verify(profileService).updateProfile(
                USER_ID,
                expectedRequest
        );
    }

    @Test
    void shouldRejectInvalidProfileFields() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());

        mockMvc.perform(put("/api/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "1invalid"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Ошибка валидации запроса"))
                .andExpect(jsonPath("$.path")
                        .value("/api/profile"))
                .andExpect(jsonPath("$.fieldErrors.username")
                        .exists());

        verify(jwtDecoder).decode(TOKEN);
        verifyNoInteractions(profileService);
    }

    @Test
    void shouldReturnNotFoundWhenProfileIsMissing()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(profileService.getProfile(USER_ID))
                .willThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Пользователь не найден"))
                .andExpect(jsonPath("$.path")
                        .value("/api/profile"));

        verify(profileService).getProfile(USER_ID);
    }

    @Test
    void shouldRejectProfileReadWithoutToken() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/profile"));

        verifyNoInteractions(jwtDecoder, profileService);
    }

    @Test
    void shouldRejectProfileUpdateWithoutToken() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/profile"));

        verifyNoInteractions(jwtDecoder, profileService);
    }

    private ProfileResponse createResponse() {
        return new ProfileResponse(
                USER_ID,
                "user@example.com",
                "Ivan",
                "Иван Иванов",
                "Vanya",
                "Переводчик",
                "Описание профиля",
                true,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private Jwt createJwt() {
        Instant issuedAt = Instant.parse("2026-07-28T07:00:00Z");

        return Jwt.withTokenValue(TOKEN)
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }
}
