package com.translatelab.backend.payment.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionPurchaseStartRequestTest {

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
    void shouldNormalizeValidPlanCode() {
        SubscriptionPurchaseStartRequest request =
                new SubscriptionPurchaseStartRequest("  PRO  ");

        assertEquals("PRO", request.planCode());
        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "PRO_2026",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidPlanCodes(String planCode) {
        SubscriptionPurchaseStartRequest request =
                new SubscriptionPurchaseStartRequest(planCode);

        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingPlanCode(String planCode) {
        SubscriptionPurchaseStartRequest request =
                new SubscriptionPurchaseStartRequest(planCode);

        assertTrue(hasPlanCodeViolation(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pro",
            "1PRO",
            "PRO-PLUS",
            "ТАРИФ",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidPlanCodes(String planCode) {
        SubscriptionPurchaseStartRequest request =
                new SubscriptionPurchaseStartRequest(planCode);

        assertTrue(hasPlanCodeViolation(request));
    }

    @Test
    void shouldUseSnakeCaseJsonContractAndNormalizeValue()
            throws Exception {
        SubscriptionPurchaseStartRequest request = objectMapper.readValue(
                "{\"plan_code\":\"  PRO  \"}",
                SubscriptionPurchaseStartRequest.class
        );

        assertEquals("PRO", request.planCode());
        assertEquals(
                "{\"plan_code\":\"PRO\"}",
                objectMapper.writeValueAsString(request)
        );
    }

    private boolean hasPlanCodeViolation(
            SubscriptionPurchaseStartRequest request
    ) {
        return validator.validate(request).stream()
                .anyMatch(violation -> violation
                        .getPropertyPath()
                        .toString()
                        .equals("planCode"));
    }
}
