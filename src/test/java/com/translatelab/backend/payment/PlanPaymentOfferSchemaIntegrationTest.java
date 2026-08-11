package com.translatelab.backend.payment;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PlanPaymentOfferSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    private String planCode;

    @BeforeEach
    void setUp() {
        planCode = uniqueCode("TEST_PLAN_");
        planRepository.saveAndFlush(
                new SubscriptionPlan(planCode, "Тестовый тариф")
        );
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM plan_payment_offers WHERE plan_code = ?",
                planCode
        );
        jdbcTemplate.update(
                "DELETE FROM subscription_plans WHERE code = ?",
                planCode
        );
    }

    @Test
    void shouldPersistValidOfferWithDatabaseDefaults() {
        String offerCode = uniqueCode("TEST_OFFER_");

        jdbcTemplate.update(
                """
                INSERT INTO plan_payment_offers (
                    code,
                    plan_code,
                    provider,
                    price_minor,
                    currency,
                    billing_period,
                    external_product_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                offerCode,
                planCode,
                "TRIBUTE",
                49_900L,
                "RUB",
                "MONTH",
                null
        );

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                SELECT active, created_at, updated_at
                FROM plan_payment_offers
                WHERE code = ?
                """,
                offerCode
        );

        assertAll(
                () -> assertEquals(Boolean.TRUE, stored.get("active")),
                () -> assertTrue(
                        stored.get("created_at") instanceof Timestamp
                ),
                () -> assertNotNull(stored.get("updated_at"))
        );
    }

    @Test
    void shouldEnforceFieldConstraints() {
        assertAll(
                () -> assertInvalidOffer(
                        "invalid_code",
                        "TRIBUTE",
                        49_900L,
                        "RUB",
                        "MONTH",
                        null
                ),
                () -> assertInvalidOffer(
                        uniqueCode("TEST_OFFER_"),
                        "tribute",
                        49_900L,
                        "RUB",
                        "MONTH",
                        null
                ),
                () -> assertInvalidOffer(
                        uniqueCode("TEST_OFFER_"),
                        "TRIBUTE",
                        0L,
                        "RUB",
                        "MONTH",
                        null
                ),
                () -> assertInvalidOffer(
                        uniqueCode("TEST_OFFER_"),
                        "TRIBUTE",
                        49_900L,
                        "rub",
                        "MONTH",
                        null
                ),
                () -> assertInvalidOffer(
                        uniqueCode("TEST_OFFER_"),
                        "TRIBUTE",
                        49_900L,
                        "RUB",
                        "YEAR",
                        null
                ),
                () -> assertInvalidOffer(
                        uniqueCode("TEST_OFFER_"),
                        "TRIBUTE",
                        49_900L,
                        "RUB",
                        "MONTH",
                        " "
                )
        );
    }

    @Test
    void shouldAllowOnlyOneActiveOfferPerPlanProviderAndPeriod() {
        insertOffer(
                uniqueCode("TEST_OFFER_"),
                "TRIBUTE",
                uniqueExternalId(),
                true
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertOffer(
                        uniqueCode("TEST_OFFER_"),
                        "TRIBUTE",
                        uniqueExternalId(),
                        true
                )
        );

        insertOffer(
                uniqueCode("TEST_OFFER_"),
                "TRIBUTE",
                uniqueExternalId(),
                false
        );

        assertEquals(2, countOffers());
    }

    @Test
    void shouldScopeExternalProductUniquenessByProvider() {
        String externalProductId = uniqueExternalId();
        insertOffer(
                uniqueCode("TEST_OFFER_"),
                "TRIBUTE",
                externalProductId,
                true
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertOffer(
                        uniqueCode("TEST_OFFER_"),
                        "TRIBUTE",
                        externalProductId,
                        false
                )
        );

        insertOffer(
                uniqueCode("TEST_OFFER_"),
                "OTHER_PROVIDER",
                externalProductId,
                true
        );

        assertEquals(2, countOffers());
    }

    @Test
    void shouldRestrictDeletionOfReferencedPlan() {
        insertOffer(
                uniqueCode("TEST_OFFER_"),
                "TRIBUTE",
                null,
                true
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "DELETE FROM subscription_plans WHERE code = ?",
                        planCode
                )
        );
    }

    private void assertInvalidOffer(
            String offerCode,
            String provider,
            long priceMinor,
            String currency,
            String billingPeriod,
            String externalProductId
    ) {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertOffer(
                        offerCode,
                        provider,
                        priceMinor,
                        currency,
                        billingPeriod,
                        externalProductId,
                        true
                )
        );
    }

    private void insertOffer(
            String offerCode,
            String provider,
            String externalProductId,
            boolean active
    ) {
        insertOffer(
                offerCode,
                provider,
                49_900L,
                "RUB",
                "MONTH",
                externalProductId,
                active
        );
    }

    private void insertOffer(
            String offerCode,
            String provider,
            long priceMinor,
            String currency,
            String billingPeriod,
            String externalProductId,
            boolean active
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO plan_payment_offers (
                    code,
                    plan_code,
                    provider,
                    price_minor,
                    currency,
                    billing_period,
                    external_product_id,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                offerCode,
                planCode,
                provider,
                priceMinor,
                currency,
                billingPeriod,
                externalProductId,
                active
        );
    }

    private Integer countOffers() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM plan_payment_offers
                WHERE plan_code = ?
                """,
                Integer.class,
                planCode
        );
    }

    private String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private String uniqueExternalId() {
        return "product-" + UUID.randomUUID();
    }
}
