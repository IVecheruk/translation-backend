package com.translatelab.backend.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateProfileRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final ObjectMapper objectMapper = JsonMapper
            .builder()
            .build();

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldNormalizeOptionalFieldsAndPreserveUsernameCase() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "  Ivan_2026  ",
                "  Иван Петров  ",
                "   ",
                null,
                "\n  Перевожу технические тексты  \t"
        );

        assertEquals("Ivan_2026", request.username());
        assertEquals("Иван Петров", request.displayName());
        assertNull(request.nickname());
        assertNull(request.profession());
        assertEquals(
                "Перевожу технические тексты",
                request.bio()
        );
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldAllowCompletelyEmptyProfile() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                " ",
                "",
                null,
                "\t"
        );

        assertNull(request.username());
        assertNull(request.displayName());
        assertNull(request.nickname());
        assertNull(request.profession());
        assertNull(request.bio());
        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ab",
            "1ivan",
            "ivan-name",
            "имя"
    })
    void shouldRejectInvalidUsername(String username) {
        UpdateProfileRequest request = new UpdateProfileRequest(
                username,
                null,
                null,
                null,
                null
        );

        assertTrue(
                validator.validate(request).stream()
                        .anyMatch(violation -> violation
                                .getPropertyPath()
                                .toString()
                                .equals("username"))
        );
    }

    @Test
    void shouldRejectTextFieldsThatExceedTheirLimits() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "ValidUser",
                "a".repeat(81),
                "b".repeat(51),
                "c".repeat(101),
                "d".repeat(1001)
        );

        Set<String> invalidFields = validator.validate(request).stream()
                .map(violation -> violation
                        .getPropertyPath()
                        .toString())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "displayName",
                        "nickname",
                        "profession",
                        "bio"
                ),
                invalidFields
        );
    }

    @Test
    void shouldDeserializeSnakeCaseJsonAndNormalizeValues()
            throws Exception {
        String json = """
                {
                  "username": "  Ivan_2026  ",
                  "display_name": "  Иван Петров  ",
                  "nickname": "  Vanya  ",
                  "profession": "  Переводчик  ",
                  "bio": "  О себе  "
                }
                """;

        UpdateProfileRequest request = objectMapper.readValue(
                json,
                UpdateProfileRequest.class
        );

        assertEquals("Ivan_2026", request.username());
        assertEquals("Иван Петров", request.displayName());
        assertEquals("Vanya", request.nickname());
        assertEquals("Переводчик", request.profession());
        assertEquals("О себе", request.bio());
    }
}
