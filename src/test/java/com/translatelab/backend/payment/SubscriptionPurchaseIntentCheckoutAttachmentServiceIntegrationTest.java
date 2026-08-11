package com.translatelab.backend.payment;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCheckoutAttachmentCommand;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.payment.service.SubscriptionPurchaseIntentCheckoutAttachmentService;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(
        SubscriptionPurchaseIntentCheckoutAttachmentServiceIntegrationTest
                .FixedClockConfiguration.class
)
class SubscriptionPurchaseIntentCheckoutAttachmentServiceIntegrationTest {

    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Autowired
    private SubscriptionPurchaseIntentCheckoutAttachmentService service;

    @Autowired
    private SubscriptionPurchaseIntentRepository intentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private PlanPaymentOfferRepository offerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> userIds = new ArrayList<>();
    private UUID intentId;
    private String planCode;
    private String offerCode;

    @AfterEach
    void cleanUp() {
        if (intentId != null) {
            jdbcTemplate.update(
                    "DELETE FROM subscription_purchase_intents WHERE id = ?",
                    intentId
            );
        }

        for (UUID userId : userIds) {
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    userId
            );
        }

        if (offerCode != null) {
            jdbcTemplate.update(
                    "DELETE FROM plan_payment_offers WHERE code = ?",
                    offerCode
            );
        }

        if (planCode != null) {
            jdbcTemplate.update(
                    "DELETE FROM subscription_plans WHERE code = ?",
                    planCode
            );
        }
    }

    @Test
    void shouldPersistCheckoutAttachmentAndAllowSameIdRetry() {
        TestIntent testIntent = savePendingIntent(
                NOW.minusSeconds(60),
                NOW.plusSeconds(1800)
        );
        SubscriptionPurchaseIntentCheckoutAttachmentCommand command = command(
                testIntent.ownerId(),
                "checkout-123"
        );

        service.attach(command);
        assertDoesNotThrow(() -> service.attach(command));

        assertEquals("checkout-123", storedCheckoutId());
    }

    @Test
    void shouldHideForeignIntentWithoutChangingIt() {
        TestIntent testIntent = savePendingIntent(
                NOW.minusSeconds(60),
                NOW.plusSeconds(1800)
        );
        User anotherUser = saveUser();

        assertThrows(
                SubscriptionPurchaseIntentNotFoundException.class,
                () -> service.attach(command(
                        anotherUser.getId(),
                        "checkout-123"
                ))
        );

        assertNull(storedCheckoutId());
    }

    @Test
    void shouldRollBackAttachmentAtExactExpirationBoundary() {
        TestIntent testIntent = savePendingIntent(
                NOW.minusSeconds(1800),
                NOW
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.attach(command(
                        testIntent.ownerId(),
                        "checkout-123"
                ))
        );

        assertNull(storedCheckoutId());
    }

    @Test
    void shouldRejectCheckoutReplacementAndPreserveOriginal() {
        TestIntent testIntent = savePendingIntent(
                NOW.minusSeconds(60),
                NOW.plusSeconds(1800)
        );
        service.attach(command(
                testIntent.ownerId(),
                "checkout-123"
        ));

        assertThrows(
                IllegalStateException.class,
                () -> service.attach(command(
                        testIntent.ownerId(),
                        "checkout-456"
                ))
        );

        assertEquals("checkout-123", storedCheckoutId());
    }

    private TestIntent savePendingIntent(
            Instant creationTime,
            Instant expiresAt
    ) {
        User user = saveUser();
        planCode = uniquePlanCode();
        SubscriptionPlan plan = planRepository.saveAndFlush(
                new SubscriptionPlan(
                        planCode,
                        "Тестовый тариф"
                )
        );
        offerCode = uniqueOfferCode();
        PlanPaymentOffer offer = new PlanPaymentOffer(
                offerCode,
                plan,
                "TRIBUTE",
                99900,
                "RUB",
                BillingPeriod.MONTH,
                "product-" + UUID.randomUUID()
        );
        offerRepository.saveAndFlush(offer);
        SubscriptionPurchaseIntent intent = intentRepository.saveAndFlush(
                SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        creationTime,
                        expiresAt
                )
        );
        intentId = intent.getId();
        return new TestIntent(user.getId());
    }

    private User saveUser() {
        User user = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        userIds.add(user.getId());
        return user;
    }

    private SubscriptionPurchaseIntentCheckoutAttachmentCommand command(
            UUID userId,
            String checkoutId
    ) {
        return new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                userId,
                intentId,
                checkoutId
        );
    }

    private String storedCheckoutId() {
        return jdbcTemplate.queryForObject(
                """
                SELECT external_checkout_id
                FROM subscription_purchase_intents
                WHERE id = ?
                """,
                String.class,
                intentId
        );
    }

    private String uniquePlanCode() {
        return "TEST_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private String uniqueOfferCode() {
        return "OFFER_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private record TestIntent(UUID ownerId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
