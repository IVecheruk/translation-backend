package com.translatelab.backend.payment;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseCompletionCommand;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntentStatus;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseConflictException;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.payment.service.SubscriptionPurchaseCompletionService;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.subscription.entity.SubscriptionStatus;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(
        SubscriptionPurchaseCompletionServiceIntegrationTest
                .FixedClockConfiguration.class
)
class SubscriptionPurchaseCompletionServiceIntegrationTest {

    private static final String PROVIDER = "TRIBUTE";
    private static final String EXTERNAL_CHECKOUT_ID = "checkout-123";
    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Instant PERIOD_END = Instant.parse(
            "2026-10-01T00:00:00Z"
    );

    @Autowired
    private SubscriptionPurchaseCompletionService service;

    @Autowired
    private SubscriptionPurchaseIntentRepository intentRepository;

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private PlanPaymentOfferRepository offerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID intentId;
    private String planCode;
    private String offerCode;
    private String externalEventId;

    @AfterEach
    void cleanUp() {
        if (externalEventId != null) {
            jdbcTemplate.update(
                    """
                    DELETE FROM processed_payment_events
                    WHERE provider = ?
                      AND external_event_id = ?
                    """,
                    PROVIDER,
                    externalEventId
            );
        }

        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM user_subscriptions WHERE user_id = ?",
                    userId
            );
        }

        if (intentId != null) {
            jdbcTemplate.update(
                    "DELETE FROM subscription_purchase_intents WHERE id = ?",
                    intentId
            );
        }

        if (userId != null) {
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
    void shouldCommitPurchaseOnceAndIgnoreDuplicateEvent() {
        savePendingIntent(NOW.plusSeconds(1800));
        externalEventId = uniqueExternalId("event");
        String externalCustomerId = uniqueExternalId("customer");
        String externalSubscriptionId = uniqueExternalId("subscription");
        SubscriptionPurchaseCompletionCommand command = command(
                externalEventId,
                EXTERNAL_CHECKOUT_ID,
                externalCustomerId,
                externalSubscriptionId
        );

        boolean firstProcessing = service.processCompletion(command);
        boolean duplicateProcessing = service.processCompletion(command);

        SubscriptionPurchaseIntent consumedIntent = intentRepository
                .findById(intentId)
                .orElseThrow();
        UserSubscription subscription = subscriptionRepository
                .findEffectiveActiveByUserIdAt(userId, NOW)
                .orElseThrow();

        assertAll(
                () -> assertTrue(firstProcessing),
                () -> assertFalse(duplicateProcessing),
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.CONSUMED,
                        consumedIntent.getStatus()
                ),
                () -> assertEquals(NOW, consumedIntent.getConsumedAt()),
                () -> assertEquals(userId, subscription.getUser().getId()),
                () -> assertEquals(
                        planCode,
                        subscription.getPlan().getCode()
                ),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        externalCustomerId,
                        subscription.getExternalCustomerId()
                ),
                () -> assertEquals(
                        externalSubscriptionId,
                        subscription.getExternalOrderId()
                ),
                () -> assertNull(subscription.getExternalSubscriptionId()),
                () -> assertEquals(1, countStoredEvents()),
                () -> assertEquals(
                        "SUBSCRIPTION_PURCHASE_COMPLETED",
                        storedEventType()
                ),
                () -> assertEquals(1, countUserSubscriptions())
        );
    }

    @Test
    void shouldRollBackEventWhenIntentDoesNotExist() {
        savePendingIntent(NOW.plusSeconds(1800));
        externalEventId = uniqueExternalId("missing-intent-event");

        assertThrows(
                SubscriptionPurchaseIntentNotFoundException.class,
                () -> service.processCompletion(command(
                        externalEventId,
                        uniqueExternalId("missing-checkout"),
                        null,
                        uniqueExternalId("subscription")
                ))
        );

        assertAll(
                () -> assertEquals(0, countStoredEvents()),
                () -> assertEquals(0, countUserSubscriptions()),
                () -> assertIntentRemainsPending()
        );
    }

    @Test
    void shouldRollBackEventAndSubscriptionForExpiredIntent() {
        savePendingIntent(NOW);
        externalEventId = uniqueExternalId("expired-intent-event");

        assertThrows(
                IllegalStateException.class,
                () -> service.processCompletion(command(
                        externalEventId,
                        EXTERNAL_CHECKOUT_ID,
                        null,
                        uniqueExternalId("subscription")
                ))
        );

        assertAll(
                () -> assertEquals(0, countStoredEvents()),
                () -> assertEquals(0, countUserSubscriptions()),
                () -> assertIntentRemainsPending()
        );
    }

    @Test
    void shouldRollBackConsumedIntentWhenActiveSubscriptionAlreadyExists() {
        TestData testData = savePendingIntent(NOW.plusSeconds(1800));
        subscriptionRepository.saveAndFlush(
                UserSubscription.providerManaged(
                        testData.user(),
                        testData.plan(),
                        NOW.minusSeconds(3600),
                        PERIOD_END,
                        PROVIDER,
                        uniqueExternalId("existing-customer"),
                        uniqueExternalId("existing-subscription")
                )
        );
        externalEventId = uniqueExternalId("conflict-event");

        assertThrows(
                SubscriptionPurchaseConflictException.class,
                () -> service.processCompletion(command(
                        externalEventId,
                        EXTERNAL_CHECKOUT_ID,
                        null,
                        uniqueExternalId("new-subscription")
                ))
        );

        assertAll(
                () -> assertEquals(0, countStoredEvents()),
                () -> assertEquals(1, countUserSubscriptions()),
                () -> assertIntentRemainsPending()
        );
    }

    private TestData savePendingIntent(Instant expiresAt) {
        User user = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        userId = user.getId();

        planCode = "TEST_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
        SubscriptionPlan plan = planRepository.saveAndFlush(
                new SubscriptionPlan(planCode, "Тестовый тариф")
        );
        offerCode = uniqueOfferCode();
        PlanPaymentOffer offer = new PlanPaymentOffer(
                offerCode,
                plan,
                PROVIDER,
                99900,
                "RUB",
                BillingPeriod.MONTH,
                null
        );
        offerRepository.saveAndFlush(offer);

        SubscriptionPurchaseIntent intent =
                SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        NOW.minusSeconds(60),
                        expiresAt
                );
        intent.attachCheckout(
                EXTERNAL_CHECKOUT_ID,
                NOW.minusSeconds(30)
        );
        intentRepository.saveAndFlush(intent);
        intentId = intent.getId();

        return new TestData(user, plan);
    }

    private SubscriptionPurchaseCompletionCommand command(
            String eventId,
            String checkoutId,
            String customerId,
            String subscriptionId
    ) {
        return new SubscriptionPurchaseCompletionCommand(
                PROVIDER,
                eventId,
                checkoutId,
                subscriptionId,
                customerId,
                null,
                99900,
                "RUB",
                BillingPeriod.MONTH,
                null,
                NOW,
                PERIOD_END
        );
    }

    private void assertIntentRemainsPending() {
        SubscriptionPurchaseIntent unchanged = intentRepository
                .findById(intentId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        unchanged.getStatus()
                ),
                () -> assertNull(unchanged.getConsumedAt())
        );
    }

    private Integer countStoredEvents() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_payment_events
                WHERE provider = ?
                  AND external_event_id = ?
                """,
                Integer.class,
                PROVIDER,
                externalEventId
        );
    }

    private String storedEventType() {
        return jdbcTemplate.queryForObject(
                """
                SELECT event_type
                FROM processed_payment_events
                WHERE provider = ?
                  AND external_event_id = ?
                """,
                String.class,
                PROVIDER,
                externalEventId
        );
    }

    private Integer countUserSubscriptions() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_subscriptions
                WHERE user_id = ?
                """,
                Integer.class,
                userId
        );
    }

    private String uniqueExternalId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String uniqueOfferCode() {
        return "OFFER_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private record TestData(User user, SubscriptionPlan plan) {
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
