package com.translatelab.backend.payment;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PurchaseIntentOfferLinkSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    private UUID userId;
    private String planCode;
    private String otherPlanCode;
    private String offerCode;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(new User(
                UUID.randomUUID() + "@example.com",
                "password-hash"
        ));
        userId = user.getId();
        planCode = uniqueCode("PLAN_");
        otherPlanCode = uniqueCode("OTHER_PLAN_");
        planRepository.saveAndFlush(new SubscriptionPlan(
                planCode,
                "Основной тестовый тариф"
        ));
        planRepository.saveAndFlush(new SubscriptionPlan(
                otherPlanCode,
                "Другой тестовый тариф"
        ));
        offerCode = uniqueCode("OFFER_");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM subscription_purchase_intents WHERE user_id = ?",
                userId
        );
        jdbcTemplate.update(
                "DELETE FROM plan_payment_offers WHERE code = ?",
                offerCode
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                userId
        );
        jdbcTemplate.update(
                "DELETE FROM subscription_plans WHERE code IN (?, ?)",
                planCode,
                otherPlanCode
        );
    }

    @Test
    void shouldAllowLegacyIntentWithoutOffer() {
        UUID intentId = UUID.randomUUID();

        insertIntent(
                intentId,
                null,
                planCode,
                "TRIBUTE"
        );

        String storedOfferCode = jdbcTemplate.queryForObject(
                """
                SELECT offer_code
                FROM subscription_purchase_intents
                WHERE id = ?
                """,
                String.class,
                intentId
        );

        assertNull(storedOfferCode);
    }

    @Test
    void shouldAcceptMatchingOfferScope() {
        insertOffer();
        UUID intentId = UUID.randomUUID();

        insertIntent(
                intentId,
                offerCode,
                planCode,
                "TRIBUTE"
        );

        String storedOfferCode = jdbcTemplate.queryForObject(
                """
                SELECT offer_code
                FROM subscription_purchase_intents
                WHERE id = ?
                """,
                String.class,
                intentId
        );

        assertEquals(offerCode, storedOfferCode);
    }

    @Test
    void shouldRejectOfferScopeMismatch() {
        insertOffer();

        assertAll(
                () -> assertThrows(
                        DataIntegrityViolationException.class,
                        () -> insertIntent(
                                UUID.randomUUID(),
                                offerCode,
                                otherPlanCode,
                                "TRIBUTE"
                        )
                ),
                () -> assertThrows(
                        DataIntegrityViolationException.class,
                        () -> insertIntent(
                                UUID.randomUUID(),
                                offerCode,
                                planCode,
                                "OTHER_PROVIDER"
                        )
                )
        );
    }

    @Test
    void shouldRestrictDeletionOfReferencedOffer() {
        insertOffer();
        insertIntent(
                UUID.randomUUID(),
                offerCode,
                planCode,
                "TRIBUTE"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "DELETE FROM plan_payment_offers WHERE code = ?",
                        offerCode
                )
        );
    }

    private void insertOffer() {
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
                99_900L,
                "RUB",
                "MONTH",
                "product-" + UUID.randomUUID()
        );
    }

    private void insertIntent(
            UUID intentId,
            String linkedOfferCode,
            String linkedPlanCode,
            String provider
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO subscription_purchase_intents (
                    id,
                    user_id,
                    plan_code,
                    provider,
                    offer_code,
                    status,
                    expires_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                intentId,
                userId,
                linkedPlanCode,
                provider,
                linkedOfferCode,
                "PENDING",
                Timestamp.from(Instant.now().plusSeconds(1800))
        );
    }

    private String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}
