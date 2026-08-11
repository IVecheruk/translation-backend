package com.translatelab.backend.user.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileResponseTest {

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldSerializeAccordingToApiContract() throws Exception {
        ProfileResponse response = new ProfileResponse(
                UUID.fromString(
                        "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
                ),
                "user@example.com",
                "Ivan_2026",
                "Иван Петров",
                "Vanya",
                "Переводчик",
                "Перевожу технические тексты",
                true,
                Instant.parse("2026-07-28T08:00:00Z"),
                Instant.parse("2026-07-28T08:30:00Z")
        );

        String json = objectMapper.writeValueAsString(response);

        assertEquals(
                "{\"user_id\":"
                        + "\"9c2ad070-a91c-4b4d-99e1-bec77130c49d\","
                        + "\"email\":\"user@example.com\","
                        + "\"username\":\"Ivan_2026\","
                        + "\"display_name\":\"Иван Петров\","
                        + "\"nickname\":\"Vanya\","
                        + "\"profession\":\"Переводчик\","
                        + "\"bio\":"
                        + "\"Перевожу технические тексты\","
                        + "\"has_avatar\":true,"
                        + "\"created_at\":\"2026-07-28T08:00:00Z\","
                        + "\"updated_at\":\"2026-07-28T08:30:00Z\"}",
                json
        );
    }
}
