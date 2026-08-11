package com.translatelab.backend.translation.dto;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.entity.TranslationErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentHistoryItemResponseTest {

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldSerializeAccordingToApiContract() throws Exception {
        DocumentHistoryItemResponse response =
                new DocumentHistoryItemResponse(
                        UUID.fromString(
                                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
                        ),
                        "en",
                        "ru",
                        FileFormat.DOCX,
                        TranslationStatus.FAILED,
                        62,
                        Instant.parse("2026-07-26T07:00:00Z"),
                        Instant.parse("2026-07-26T07:05:00Z"),
                        TranslationErrorCode.TRANSLATION_FAILED,
                        "Не удалось перевести документ"
                );

        String json = objectMapper.writeValueAsString(response);

        assertEquals(
                "{\"job_id\":"
                        + "\"9c2ad070-a91c-4b4d-99e1-bec77130c49d\","
                        + "\"source_lang\":\"en\","
                        + "\"target_lang\":\"ru\","
                        + "\"format\":\"docx\","
                        + "\"status\":\"FAILED\","
                        + "\"progress\":62,"
                        + "\"created_at\":\"2026-07-26T07:00:00Z\","
                        + "\"updated_at\":\"2026-07-26T07:05:00Z\","
                        + "\"error_code\":\"TRANSLATION_FAILED\","
                        + "\"error_message\":"
                        + "\"Не удалось перевести документ\"}",
                json
        );
    }
}
