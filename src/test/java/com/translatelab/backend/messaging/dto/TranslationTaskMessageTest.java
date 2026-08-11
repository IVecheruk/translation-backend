package com.translatelab.backend.messaging.dto;

import com.translatelab.backend.translation.entity.FileFormat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationTaskMessageTest {

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .build();

    @Test
    void shouldSerializeAccordingToMlServiceContract()
            throws Exception {
        UUID jobId = UUID.fromString(
                "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
        );
        UUID eventId = UUID.fromString(
                "32013136-d69f-4a77-b83e-81a529ad3f7d"
        );
        TranslationTaskMessage message = new TranslationTaskMessage(
                eventId,
                jobId,
                "uploads/user-id/file-id.docx",
                "results/user-id/result-id.docx",
                "en",
                "ru",
                FileFormat.DOCX
        );

        String json = objectMapper.writeValueAsString(message);

        assertEquals(
                "{\"event_id\":\"32013136-d69f-4a77-b83e-81a529ad3f7d\","
                        + "\"job_id\":\"9c2ad070-a91c-4b4d-99e1-bec77130c49d\","
                        + "\"file_key\":\"uploads/user-id/file-id.docx\","
                        + "\"result_file_key\":\"results/user-id/result-id.docx\","
                        + "\"source_lang\":\"en\","
                        + "\"target_lang\":\"ru\","
                        + "\"format\":\"docx\"}",
                json
        );
    }
}
