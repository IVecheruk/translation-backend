package com.translatelab.backend.translation.dto;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentHistoryResponseTest {

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldSerializeAccordingToApiContract() throws Exception {
        DocumentHistoryResponse response =
                new DocumentHistoryResponse(
                        List.of(historyItem()),
                        0,
                        20,
                        21,
                        2,
                        true,
                        false
                );

        String json = objectMapper.writeValueAsString(response);

        assertEquals(
                "{\"items\":[{"
                        + "\"job_id\":"
                        + "\"9c2ad070-a91c-4b4d-99e1-bec77130c49d\","
                        + "\"source_lang\":\"en\","
                        + "\"target_lang\":\"ru\","
                        + "\"format\":\"docx\","
                        + "\"status\":\"DONE\","
                        + "\"progress\":100,"
                        + "\"created_at\":\"2026-07-26T07:00:00Z\","
                        + "\"updated_at\":\"2026-07-26T07:05:00Z\","
                        + "\"error_code\":null,"
                        + "\"error_message\":null}],"
                        + "\"page\":0,"
                        + "\"size\":20,"
                        + "\"total_elements\":21,"
                        + "\"total_pages\":2,"
                        + "\"first\":true,"
                        + "\"last\":false}",
                json
        );
    }

    @Test
    void shouldCreateImmutableCopyOfItems() {
        List<DocumentHistoryItemResponse> mutableItems =
                new ArrayList<>();
        mutableItems.add(historyItem());

        DocumentHistoryResponse response =
                new DocumentHistoryResponse(
                        mutableItems,
                        0,
                        20,
                        1,
                        1,
                        true,
                        true
                );
        mutableItems.clear();

        assertEquals(1, response.items().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> response.items().clear()
        );
    }

    @Test
    void shouldRejectNullItems() {
        assertThrows(
                NullPointerException.class,
                () -> new DocumentHistoryResponse(
                        null,
                        0,
                        20,
                        0,
                        0,
                        true,
                        true
                )
        );
    }

    @Test
    void shouldRejectInvalidPaginationMetadata() {
        List<DocumentHistoryItemResponse> items = List.of();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DocumentHistoryResponse(
                                items, -1, 20, 0, 0, true, true
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DocumentHistoryResponse(
                                items, 0, 0, 0, 0, true, true
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DocumentHistoryResponse(
                                items, 0, 20, -1, 0, true, true
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new DocumentHistoryResponse(
                                items, 0, 20, 0, -1, true, true
                        )
                )
        );
    }

    private DocumentHistoryItemResponse historyItem() {
        return new DocumentHistoryItemResponse(
                UUID.fromString(
                        "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
                ),
                "en",
                "ru",
                FileFormat.DOCX,
                TranslationStatus.DONE,
                100,
                Instant.parse("2026-07-26T07:00:00Z"),
                Instant.parse("2026-07-26T07:05:00Z"),
                null,
                null
        );
    }
}
