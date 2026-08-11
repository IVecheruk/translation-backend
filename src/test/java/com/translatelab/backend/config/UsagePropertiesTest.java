package com.translatelab.backend.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsagePropertiesTest {

    private static final Duration RESERVATION_TTL =
            Duration.ofMinutes(15);
    private static final Duration CLEANUP_INTERVAL =
            Duration.ofMinutes(1);
    private static final int CLEANUP_BATCH_SIZE = 100;

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
    void shouldCreatePropertiesWithValidValues() {
        UsageProperties properties = new UsageProperties(
                RESERVATION_TTL,
                CLEANUP_INTERVAL,
                CLEANUP_BATCH_SIZE
        );

        assertEquals(
                RESERVATION_TTL,
                properties.reservationTtl()
        );
        assertEquals(
                CLEANUP_INTERVAL,
                properties.cleanupInterval()
        );
        assertEquals(
                CLEANUP_BATCH_SIZE,
                properties.cleanupBatchSize()
        );
        assertTrue(validator.validate(properties).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveReservationTtl(Duration reservationTtl) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UsageProperties(
                        reservationTtl,
                        CLEANUP_INTERVAL,
                        CLEANUP_BATCH_SIZE
                )
        );

        assertEquals(
                "Срок действия резервации должен быть положительным",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingReservationTtlDuringValidation() {
        UsageProperties properties = new UsageProperties(
                null,
                CLEANUP_INTERVAL,
                CLEANUP_BATCH_SIZE
        );

        assertTrue(
                validator.validate(properties).stream()
                        .anyMatch(violation -> violation
                                .getPropertyPath()
                                .toString()
                                .equals("reservationTtl"))
        );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositiveCleanupInterval(
            Duration cleanupInterval
    ) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UsageProperties(
                        RESERVATION_TTL,
                        cleanupInterval,
                        CLEANUP_BATCH_SIZE
                )
        );

        assertEquals(
                "Интервал очистки должен быть положительным",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingCleanupIntervalDuringValidation() {
        UsageProperties properties = new UsageProperties(
                RESERVATION_TTL,
                null,
                CLEANUP_BATCH_SIZE
        );

        assertTrue(
                validator.validate(properties).stream()
                        .anyMatch(violation -> violation
                                .getPropertyPath()
                                .toString()
                                .equals("cleanupInterval"))
        );
    }

    @ParameterizedTest
    @MethodSource("nonPositiveBatchSizes")
    void shouldRejectNonPositiveCleanupBatchSize(
            int cleanupBatchSize
    ) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new UsageProperties(
                        RESERVATION_TTL,
                        CLEANUP_INTERVAL,
                        cleanupBatchSize
                )
        );

        assertEquals(
                "Размер пакета очистки должен быть положительным",
                exception.getMessage()
        );
    }

    private static Stream<Duration> nonPositiveDurations() {
        return Stream.of(
                Duration.ZERO,
                Duration.ofNanos(-1),
                Duration.ofMinutes(-15)
        );
    }

    private static Stream<Integer> nonPositiveBatchSizes() {
        return Stream.of(0, -1, -100);
    }
}
