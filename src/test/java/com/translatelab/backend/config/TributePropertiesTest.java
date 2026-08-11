package com.translatelab.backend.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TributePropertiesTest {

    private static final URI BASE_URL = URI.create(
            "https://tribute.tg/api/v1"
    );
    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT =
            Duration.ofSeconds(10);
    private static final String API_KEY = "secret-api-key-value";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

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
    void shouldCreateEnabledPropertiesWithExactValues() {
        TributeProperties properties = validProperties(true, API_KEY);

        assertAll(
                () -> assertTrue(properties.enabled()),
                () -> assertEquals(BASE_URL, properties.baseUrl()),
                () -> assertEquals(API_KEY, properties.apiKey()),
                () -> assertEquals(
                        CONNECT_TIMEOUT,
                        properties.connectTimeout()
                ),
                () -> assertEquals(
                        READ_TIMEOUT,
                        properties.readTimeout()
                ),
                () -> assertTrue(validator.validate(properties).isEmpty())
        );
    }

    @Test
    void shouldAllowMissingApiKeyWhileIntegrationIsDisabled() {
        TributeProperties properties = validProperties(false, null);

        assertAll(
                () -> assertFalse(properties.enabled()),
                () -> assertNull(properties.apiKey()),
                () -> assertTrue(validator.validate(properties).isEmpty())
        );
    }

    @Test
    void shouldRedactApiKeyFromStringRepresentation() {
        TributeProperties properties = validProperties(true, API_KEY);

        String representation = properties.toString();

        assertAll(
                () -> assertFalse(representation.contains(API_KEY)),
                () -> assertTrue(
                        representation.contains("apiKey=<redacted>")
                ),
                () -> assertTrue(
                        representation.contains("enabled=true")
                ),
                () -> assertTrue(
                        representation.contains(BASE_URL.toString())
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void shouldRequireApiKeyWhenIntegrationIsEnabled(String apiKey) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validProperties(true, apiKey)
        );

        assertEquals(
                "Для включения Tribute необходимо настроить API-ключ",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @MethodSource("invalidBaseUrls")
    void shouldRejectInvalidBaseUrlShape(URI baseUrl) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TributeProperties(
                        false,
                        baseUrl,
                        null,
                        CONNECT_TIMEOUT,
                        READ_TIMEOUT
                )
        );

        assertEquals(
                "Базовый адрес Tribute должен быть абсолютным HTTPS URL",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @MethodSource("unsafeBaseUrls")
    void shouldRejectUnsafeBaseUrlComponents(URI baseUrl) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TributeProperties(
                        false,
                        baseUrl,
                        null,
                        CONNECT_TIMEOUT,
                        READ_TIMEOUT
                )
        );

        assertEquals(
                "Базовый адрес Tribute не должен содержать "
                        + "учётные данные, параметры или фрагмент",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveConnectTimeout(Duration timeout) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TributeProperties(
                        false,
                        BASE_URL,
                        null,
                        timeout,
                        READ_TIMEOUT
                )
        );

        assertEquals(
                "Таймаут подключения к Tribute должен быть положительным",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveReadTimeout(Duration timeout) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TributeProperties(
                        false,
                        BASE_URL,
                        null,
                        CONNECT_TIMEOUT,
                        timeout
                )
        );

        assertEquals(
                "Таймаут ответа Tribute должен быть положительным",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectConnectTimeoutAboveMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TributeProperties(
                        false,
                        BASE_URL,
                        null,
                        Duration.ofSeconds(10).plusNanos(1),
                        READ_TIMEOUT
                )
        );

        assertEquals(
                "Таймаут подключения к Tribute не должен превышать PT10S",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectReadTimeoutAboveMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TributeProperties(
                        false,
                        BASE_URL,
                        null,
                        CONNECT_TIMEOUT,
                        Duration.ofSeconds(30).plusNanos(1)
                )
        );

        assertEquals(
                "Таймаут ответа Tribute не должен превышать PT30S",
                exception.getMessage()
        );
    }

    @Test
    void shouldAcceptExactTimeoutMaximums() {
        TributeProperties properties = new TributeProperties(
                true,
                BASE_URL,
                API_KEY,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30)
        );

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void shouldRejectApiKeyDestinationOutsideOfficialTributeHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TributeProperties(
                        true,
                        URI.create("https://attacker.example/api/v1"),
                        API_KEY,
                        CONNECT_TIMEOUT,
                        READ_TIMEOUT
                )
        );
    }

    @Test
    void shouldReportMissingBaseUrlDuringValidation() {
        TributeProperties properties = new TributeProperties(
                false,
                null,
                null,
                CONNECT_TIMEOUT,
                READ_TIMEOUT
        );

        assertTrue(hasViolation(properties, "baseUrl"));
    }

    @Test
    void shouldReportMissingConnectTimeoutDuringValidation() {
        TributeProperties properties = new TributeProperties(
                false,
                BASE_URL,
                null,
                null,
                READ_TIMEOUT
        );

        assertTrue(hasViolation(properties, "connectTimeout"));
    }

    @Test
    void shouldReportMissingReadTimeoutDuringValidation() {
        TributeProperties properties = new TributeProperties(
                false,
                BASE_URL,
                null,
                CONNECT_TIMEOUT,
                null
        );

        assertTrue(hasViolation(properties, "readTimeout"));
    }

    private TributeProperties validProperties(
            boolean enabled,
            String apiKey
    ) {
        return new TributeProperties(
                enabled,
                BASE_URL,
                apiKey,
                CONNECT_TIMEOUT,
                READ_TIMEOUT
        );
    }

    private boolean hasViolation(
            TributeProperties properties,
            String propertyName
    ) {
        Set<ConstraintViolation<TributeProperties>> violations =
                validator.validate(properties);

        return violations.stream()
                .anyMatch(violation -> violation
                        .getPropertyPath()
                        .toString()
                        .equals(propertyName));
    }

    private static Stream<URI> invalidBaseUrls() {
        return Stream.of(
                URI.create("/api/v1"),
                URI.create("http://tribute.tg/api/v1"),
                URI.create("ftp://tribute.tg/api/v1"),
                URI.create("https:///api/v1")
        );
    }

    private static Stream<URI> unsafeBaseUrls() {
        return Stream.of(
                URI.create("https://user@tribute.tg/api/v1"),
                URI.create("https://tribute.tg/api/v1?debug=true"),
                URI.create("https://tribute.tg/api/v1#orders")
        );
    }

    private static Stream<Duration> nonPositiveDurations() {
        return Stream.of(
                Duration.ZERO,
                Duration.ofNanos(-1),
                Duration.ofMinutes(-1)
        );
    }
}
