package com.translatelab.backend.translation;

import com.translatelab.backend.common.exception.ApiError;
import com.translatelab.backend.translation.service.DocumentUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.document-upload.max-file-size=10B",
                "spring.servlet.multipart.max-file-size=10B",
                "spring.servlet.multipart.max-request-size=100KB"
        }
)
class DocumentMultipartSizeIntegrationTest {

    private static final String ACCESS_TOKEN = "multipart-limit-token";
    private static final UUID USER_ID = UUID.fromString(
            "11ed17eb-dc07-42d8-bd76-028415603c4d"
    );

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private DocumentUploadService documentUploadService;

    @BeforeEach
    void setUpJwt() {
        Instant issuedAt = Instant.now();
        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .build();

        given(jwtDecoder.decode(ACCESS_TOKEN)).willReturn(jwt);
    }

    @Test
    void shouldReturnContentTooLargeBeforeCallingUploadService()
            throws Exception {
        ByteArrayResource file = new ByteArrayResource(new byte[11]);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        fileHeaders.setContentDisposition(
                ContentDisposition.formData()
                        .name("file")
                        .filename("document.docx")
                        .build()
        );

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(file, fileHeaders));

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> restClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/documents/upload")
                                .queryParam("source_lang", "en")
                                .queryParam("target_lang", "ru")
                                .build()
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + ACCESS_TOKEN
                        )
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity()
        );

        ApiError error = objectMapper.readValue(
                exception.getResponseBodyAsByteArray(),
                ApiError.class
        );

        assertAll(
                () -> assertEquals(413, exception.getStatusCode().value()),
                () -> assertEquals(413, error.status()),
                () -> assertEquals(
                        "Размер документа превышает максимально допустимый",
                        error.message()
                ),
                () -> assertEquals(
                        "/api/documents/upload",
                        error.path()
                ),
                () -> assertNotNull(error.timestamp()),
                () -> assertNotNull(error.fieldErrors()),
                () -> assertEquals(0, error.fieldErrors().size())
        );
        verifyNoInteractions(documentUploadService);
    }
}
