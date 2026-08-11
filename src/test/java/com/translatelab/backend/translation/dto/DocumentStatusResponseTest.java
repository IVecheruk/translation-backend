package com.translatelab.backend.translation.dto;

import com.translatelab.backend.translation.entity.TranslationStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentStatusResponseTest {

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .build();

    @Test
    void shouldSerializeAccordingToApiContract() throws Exception {
        UUID jobId = UUID.fromString(
                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
        );
        DocumentStatusResponse response = new DocumentStatusResponse(
                jobId,
                TranslationStatus.PENDING,
                0,
                null,
                null
        );

        String json = objectMapper.writeValueAsString(response);

        assertEquals(
                "{\"job_id\":"
                        + "\"9c2ad070-a91c-4b4d-99e1-bec77130c49d\","
                        + "\"status\":\"PENDING\","
                        + "\"progress\":0,"
                        + "\"error_code\":null,"
                        + "\"error_message\":null}",
                json
        );
    }
}
