package com.translatelab.backend.messaging.dto;

import com.translatelab.backend.translation.entity.TranslationStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranslationStatusMessageTest {

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .build();

    @Test
    void shouldSerializeAccordingToMlServiceContract()
            throws Exception {
        TranslationStatusMessage message = new TranslationStatusMessage(
                UUID.fromString(
                        "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
                ),
                TranslationStatus.DONE,
                100,
                "results/user-id/file-id.docx",
                null
        );

        String json = objectMapper.writeValueAsString(message);

        assertEquals(
                "{\"job_id\":\"9c2ad070-a91c-4b4d-99e1-bec77130c49d\","
                        + "\"status\":\"DONE\","
                        + "\"progress\":100,"
                        + "\"result_file_key\":"
                        + "\"results/user-id/file-id.docx\","
                        + "\"error_message\":null}",
                json
        );
    }

    @Test
    void shouldRejectDiagnosticMessageAboveContractLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TranslationStatusMessage(
                        UUID.randomUUID(),
                        TranslationStatus.FAILED,
                        10,
                        null,
                        "x".repeat(
                                TranslationStatusMessage
                                        .MAX_ERROR_MESSAGE_LENGTH + 1
                        )
                )
        );
    }
}
