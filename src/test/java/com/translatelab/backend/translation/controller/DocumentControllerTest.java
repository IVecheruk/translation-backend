package com.translatelab.backend.translation.controller;

import com.translatelab.backend.common.exception.GlobalExceptionHandler;
import com.translatelab.backend.common.security.RestSecurityErrorHandler;
import com.translatelab.backend.config.SecurityConfig;
import com.translatelab.backend.translation.dto.DocumentDownloadResult;
import com.translatelab.backend.translation.dto.DocumentHistoryItemResponse;
import com.translatelab.backend.translation.dto.DocumentHistoryResponse;
import com.translatelab.backend.translation.dto.DocumentUploadResponse;
import com.translatelab.backend.translation.dto.DocumentStatusResponse;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.exception.InvalidDocumentUploadException;
import com.translatelab.backend.translation.exception.InvalidPaginationException;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.exception.TranslationResultNotReadyException;
import com.translatelab.backend.translation.service.DocumentDownloadService;
import com.translatelab.backend.translation.service.DocumentHistoryService;
import com.translatelab.backend.translation.service.DocumentStatusService;
import com.translatelab.backend.translation.service.DocumentUploadService;
import com.translatelab.backend.user.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        RestSecurityErrorHandler.class
})
class DocumentControllerTest {

    private static final UUID USER_ID = UUID.fromString(
            "584175c1-d670-4ef5-91e4-896ffb80b9cc"
    );
    private static final UUID JOB_ID = UUID.fromString(
            "10cf4338-5af4-47c0-b322-c17a283a9674"
    );
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentUploadService documentUploadService;

    @MockitoBean
    private DocumentStatusService documentStatusService;

    @MockitoBean
    private DocumentDownloadService documentDownloadService;

    @MockitoBean
    private DocumentHistoryService documentHistoryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAcceptAuthenticatedDocumentUpload() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentUploadService.upload(
                eq(USER_ID),
                any(MultipartFile.class),
                eq("en"),
                eq("ru")
        )).willReturn(new DocumentUploadResponse(JOB_ID));

        MockMultipartFile file = createFile();

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("source_lang", "en")
                        .param("target_lang", "ru")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.job_id")
                        .value(JOB_ID.toString()));

        verify(jwtDecoder).decode(TOKEN);
        verify(documentUploadService).upload(
                eq(USER_ID),
                any(MultipartFile.class),
                eq("en"),
                eq("ru")
        );
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(createFile())
                        .param("source_lang", "en")
                        .param("target_lang", "ru"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/upload"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verifyNoInteractions(
                jwtDecoder,
                documentUploadService
        );
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsInvalid() throws Exception {
        given(jwtDecoder.decode("invalid-token"))
                .willThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(createFile())
                        .param("source_lang", "en")
                        .param("target_lang", "ru")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/upload"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(jwtDecoder).decode("invalid-token");
        verifyNoInteractions(documentUploadService);
    }

    @Test
    void shouldReturnBadRequestWhenUploadIsInvalid() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentUploadService.upload(
                eq(USER_ID),
                any(MultipartFile.class),
                eq("en"),
                eq("en")
        )).willThrow(new InvalidDocumentUploadException(
                "Исходный и целевой языки должны различаться"
        ));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(createFile())
                        .param("source_lang", "en")
                        .param("target_lang", "en")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Исходный и целевой языки должны различаться"
                ))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/upload"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void shouldReturnNotFoundWhenAuthenticatedUserIsMissing()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentUploadService.upload(
                eq(USER_ID),
                any(MultipartFile.class),
                eq("en"),
                eq("ru")
        )).willThrow(new UserNotFoundException());

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(createFile())
                        .param("source_lang", "en")
                        .param("target_lang", "ru")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Пользователь не найден"))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/upload"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void shouldReturnStatusOfAuthenticatedUsersJob() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentStatusService.getStatus(USER_ID, JOB_ID))
                .willReturn(new DocumentStatusResponse(
                        JOB_ID,
                        TranslationStatus.PROCESSING,
                        37,
                        null,
                        null
                ));

        mockMvc.perform(get(
                        "/api/documents/{jobId}/status",
                        JOB_ID
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TOKEN
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.job_id")
                        .value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.progress").value(37))
                .andExpect(jsonPath("$.error_code").isEmpty())
                .andExpect(jsonPath("$.error_message").isEmpty());

        verify(documentStatusService).getStatus(USER_ID, JOB_ID);
    }

    @Test
    void shouldHideMissingOrForeignJob() throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentStatusService.getStatus(USER_ID, JOB_ID))
                .willThrow(new TranslationJobNotFoundException());

        mockMvc.perform(get(
                        "/api/documents/{jobId}/status",
                        JOB_ID
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TOKEN
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Задание перевода не найдено"))
                .andExpect(jsonPath("$.path").value(
                        "/api/documents/" + JOB_ID + "/status"
                ));
    }

    @Test
    void shouldRejectStatusRequestWithoutToken() throws Exception {
        mockMvc.perform(get(
                        "/api/documents/{jobId}/status",
                        JOB_ID
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"));

        verifyNoInteractions(documentStatusService);
    }

    @Test
    void shouldDownloadCompletedTranslation() throws Exception {
        byte[] fileContent = "translated document".getBytes(
                StandardCharsets.UTF_8
        );
        String fileName = "translation-" + JOB_ID + ".docx";
        String contentType =
                "application/vnd.openxmlformats-officedocument"
                        + ".wordprocessingml.document";
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentDownloadService.download(USER_ID, JOB_ID))
                .willReturn(new DocumentDownloadResult(
                        new ByteArrayInputStream(fileContent),
                        fileName,
                        contentType
                ));

        mockMvc.perform(get(
                        "/api/documents/{jobId}/download",
                        JOB_ID
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TOKEN
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\""
                ))
                .andExpect(content().bytes(fileContent));

        verify(documentDownloadService).download(USER_ID, JOB_ID);
    }

    @Test
    void shouldReturnConflictWhenTranslationResultIsNotReady()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentDownloadService.download(USER_ID, JOB_ID))
                .willThrow(new TranslationResultNotReadyException());

        mockMvc.perform(get(
                        "/api/documents/{jobId}/download",
                        JOB_ID
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TOKEN
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Результат перевода недоступен для скачивания"
                ));
    }

    @Test
    void shouldHideMissingOrForeignDownloadJob()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentDownloadService.download(USER_ID, JOB_ID))
                .willThrow(new TranslationJobNotFoundException());

        mockMvc.perform(get(
                        "/api/documents/{jobId}/download",
                        JOB_ID
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TOKEN
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Задание перевода не найдено"));
    }

    @Test
    void shouldRejectDownloadRequestWithoutToken()
            throws Exception {
        mockMvc.perform(get(
                        "/api/documents/{jobId}/download",
                        JOB_ID
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"));

        verifyNoInteractions(documentDownloadService);
    }

    @Test
    void shouldReturnHistoryWithDefaultPagination()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentHistoryService.getHistory(USER_ID, 0, 20))
                .willReturn(new DocumentHistoryResponse(
                        List.of(),
                        0,
                        20,
                        0,
                        0,
                        true,
                        true
                ));

        mockMvc.perform(get("/api/documents/history")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.total_pages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        verify(documentHistoryService).getHistory(USER_ID, 0, 20);
    }

    @Test
    void shouldReturnRequestedHistoryPage() throws Exception {
        Instant createdAt = Instant.parse("2026-07-26T08:00:00Z");
        Instant updatedAt = Instant.parse("2026-07-26T08:05:00Z");
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentHistoryService.getHistory(USER_ID, 1, 10))
                .willReturn(new DocumentHistoryResponse(
                        List.of(new DocumentHistoryItemResponse(
                                JOB_ID,
                                "en",
                                "ru",
                                FileFormat.DOCX,
                                TranslationStatus.DONE,
                                100,
                                createdAt,
                                updatedAt,
                                null,
                                null
                        )),
                        1,
                        10,
                        11,
                        2,
                        false,
                        true
                ));

        mockMvc.perform(get("/api/documents/history")
                        .param("page", "1")
                        .param("size", "10")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].job_id")
                        .value(JOB_ID.toString()))
                .andExpect(jsonPath("$.items[0].format")
                        .value("docx"))
                .andExpect(jsonPath("$.items[0].status")
                        .value("DONE"))
                .andExpect(jsonPath("$.items[0].progress")
                        .value(100))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total_elements").value(11))
                .andExpect(jsonPath("$.total_pages").value(2));

        verify(documentHistoryService).getHistory(USER_ID, 1, 10);
    }

    @Test
    void shouldReturnBadRequestForInvalidHistoryPagination()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());
        given(documentHistoryService.getHistory(USER_ID, -1, 20))
                .willThrow(new InvalidPaginationException(
                        "Номер страницы не должен быть отрицательным"
                ));

        mockMvc.perform(get("/api/documents/history")
                        .param("page", "-1")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Номер страницы не должен быть отрицательным"
                ))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/history"));
    }

    @Test
    void shouldRejectHistoryRequestWithoutToken()
            throws Exception {
        mockMvc.perform(get("/api/documents/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Требуется аутентификация"));

        verifyNoInteractions(documentHistoryService);
    }

    @Test
    void shouldReturnBadRequestForNonNumericHistoryPage()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());

        mockMvc.perform(get("/api/documents/history")
                        .param("page", "abc")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Некорректный параметр запроса"
                ))
                .andExpect(jsonPath("$.path")
                        .value("/api/documents/history"))
                .andExpect(jsonPath("$.fieldErrors.page")
                        .value("Некорректное значение"));

        verifyNoInteractions(documentHistoryService);
    }

    @Test
    void shouldReturnBadRequestForInvalidJobId()
            throws Exception {
        given(jwtDecoder.decode(TOKEN)).willReturn(createJwt());

        mockMvc.perform(get(
                        "/api/documents/{jobId}/status",
                        "not-a-uuid"
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + TOKEN
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Некорректный параметр запроса"
                ))
                .andExpect(jsonPath("$.fieldErrors.jobId")
                        .value("Некорректное значение"));

        verifyNoInteractions(documentStatusService);
    }

    private MockMultipartFile createFile() {
        return new MockMultipartFile(
                "file",
                "document.docx",
                "application/vnd.openxmlformats-officedocument"
                        + ".wordprocessingml.document",
                "document content".getBytes(StandardCharsets.UTF_8)
        );
    }

    private Jwt createJwt() {
        Instant issuedAt = Instant.parse("2026-07-24T10:00:00Z");

        return Jwt.withTokenValue(TOKEN)
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }
}
