package com.translatelab.backend.config;

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

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentPropertiesTest {

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
    void shouldCreatePropertiesWithPositiveTtl() {
        Duration ttl = Duration.ofMinutes(30);

        PaymentProperties properties = new PaymentProperties(
                ttl,
                "TRIBUTE"
        );

        assertEquals(ttl, properties.purchaseIntentTtl());
        assertEquals("TRIBUTE", properties.provider());
        assertTrue(validator.validate(properties).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("nonPositiveDurations")
    void shouldRejectNonPositivePurchaseIntentTtl(Duration ttl) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentProperties(ttl, "TRIBUTE")
        );

        assertEquals(
                "Срок действия заявки на покупку должен быть положительным",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingTtlDuringValidation() {
        PaymentProperties properties = new PaymentProperties(
                null,
                "TRIBUTE"
        );

        assertTrue(
                validator.validate(properties).stream()
                        .anyMatch(violation -> violation
                                .getPropertyPath()
                                .toString()
                                .equals("purchaseIntentTtl"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "TRIBUTE_2",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidProviderCodes(String provider) {
        PaymentProperties properties = new PaymentProperties(
                Duration.ofMinutes(30),
                provider
        );

        assertEquals(provider, properties.provider());
        assertTrue(validator.validate(properties).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "TRIBUTE-PAY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidProviderDuringValidation(String provider) {
        PaymentProperties properties = new PaymentProperties(
                Duration.ofMinutes(30),
                provider
        );

        assertTrue(
                validator.validate(properties).stream()
                        .anyMatch(violation -> violation
                                .getPropertyPath()
                                .toString()
                                .equals("provider"))
        );
    }

    private static Stream<Duration> nonPositiveDurations() {
        return Stream.of(
                Duration.ZERO,
                Duration.ofNanos(-1),
                Duration.ofMinutes(-30)
        );
    }
}
