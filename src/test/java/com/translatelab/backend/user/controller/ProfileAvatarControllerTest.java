package com.translatelab.backend.user.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.user.dto.AvatarDownloadResult;
import com.translatelab.backend.user.exception.AvatarNotFoundException;
import com.translatelab.backend.user.exception.InvalidAvatarException;
import com.translatelab.backend.user.service.AvatarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileAvatarController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class ProfileAvatarControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "647b83b0-3277-439f-9248-6943f0afe433"
    );
    private static final String TOKEN = "valid-token";
    private static final String AVATAR_PATH =
            "/api/profile/avatar";
    private static final byte[] AVATAR_BYTES = {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF,
            0x01
    };

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvatarService avatarService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldUploadAuthenticatedUsersAvatar()
            throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                AVATAR_BYTES
        );
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());

        mockMvc.perform(putMultipart(file)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(jwtDecoder).decode(TOKEN);
        verify(avatarService).uploadAvatar(USER_ID, file);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE
    })
    void shouldDownloadAuthenticatedUsersAvatar(
            String contentType
    ) throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(avatarService.downloadAvatar(USER_ID))
                .willReturn(new AvatarDownloadResult(
                        new ByteArrayInputStream(AVATAR_BYTES),
                        contentType
                ));

        mockMvc.perform(get(AVATAR_PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(content().bytes(AVATAR_BYTES))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        CacheControl.noStore().getHeaderValue()
                ));

        verify(jwtDecoder).decode(TOKEN);
        verify(avatarService).downloadAvatar(USER_ID);
    }

    @Test
    void shouldDeleteAuthenticatedUsersAvatar()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());

        mockMvc.perform(delete(AVATAR_PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(jwtDecoder).decode(TOKEN);
        verify(avatarService).deleteAvatar(USER_ID);
    }

    @Test
    void shouldReturnNotFoundWhenAvatarIsMissing()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(avatarService.downloadAvatar(USER_ID))
                .willThrow(new AvatarNotFoundException());

        mockMvc.perform(get(AVATAR_PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Аватар не найден"))
                .andExpect(jsonPath("$.path")
                        .value(AVATAR_PATH));

        verify(avatarService).downloadAvatar(USER_ID);
    }

    @Test
    void shouldReturnBadRequestForInvalidAvatar()
            throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.gif",
                MediaType.IMAGE_GIF_VALUE,
                new byte[]{1, 2, 3}
        );
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        willThrow(new InvalidAvatarException(
                "Поддерживаются только изображения JPEG и PNG"
        )).given(avatarService).uploadAvatar(
                eq(USER_ID),
                any()
        );

        mockMvc.perform(putMultipart(file)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Поддерживаются только изображения JPEG и PNG"
                ))
                .andExpect(jsonPath("$.path")
                        .value(AVATAR_PATH));
    }

    @Test
    void shouldRejectUploadWithoutFilePart()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());

        mockMvc.perform(multipart(AVATAR_PATH)
                        .with(putMethod())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Отсутствует обязательная часть запроса"
                ))
                .andExpect(jsonPath("$.path")
                        .value(AVATAR_PATH))
                .andExpect(jsonPath("$.fieldErrors.file").value(
                        "Обязательная часть запроса отсутствует"
                ))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));

        verifyNoInteractions(avatarService);
    }

    @Test
    void shouldRejectAvatarUploadWithoutToken()
            throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                AVATAR_BYTES
        );

        mockMvc.perform(putMultipart(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value(AVATAR_PATH));

        verifyNoInteractions(jwtDecoder, avatarService);
    }

    @Test
    void shouldRejectAvatarDownloadWithoutToken()
            throws Exception {
        mockMvc.perform(get(AVATAR_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value(AVATAR_PATH));

        verifyNoInteractions(jwtDecoder, avatarService);
    }

    @Test
    void shouldRejectAvatarDeletionWithoutToken()
            throws Exception {
        mockMvc.perform(delete(AVATAR_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value(AVATAR_PATH));

        verifyNoInteractions(jwtDecoder, avatarService);
    }

    private MockMultipartHttpServletRequestBuilder putMultipart(
            MockMultipartFile file
    ) {
        return multipart(AVATAR_PATH)
                .file(file)
                .with(putMethod());
    }

    private RequestPostProcessor putMethod() {
        return request -> {
            request.setMethod("PUT");
            return request;
        };
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
