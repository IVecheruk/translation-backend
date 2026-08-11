package com.translatelab.backend.payment;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchasePreparationResult;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntentStatus;
import com.translatelab.backend.payment.exception.PlanPaymentOfferNotFoundException;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseConflictException;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.payment.service.SubscriptionPurchaseIntentCreationService;
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
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource(properties = {
        "app.payment.purchase-intent-ttl=30m"
})
@Import(
        SubscriptionPurchaseIntentCreationServiceIntegrationTest
                .FixedClockConfiguration.class
)
class SubscriptionPurchaseIntentCreationServiceIntegrationTest {

    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Instant EXPIRES_AT = NOW.plus(
            Duration.ofMinutes(30)
    );

    @Autowired
    private SubscriptionPurchaseIntentCreationService service;

    @Autowired
    private SubscriptionPurchaseIntentRepository intentRepository;

    @Autowired
    private PlanPaymentOfferRepository offerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID intentId;
    private String planCode;
    private String offerCode;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM processed_payment_events "
                            + "WHERE external_event_id LIKE ?",
                    "%" + userId + "%"
            );
            jdbcTemplate.update(
                    "DELETE FROM user_subscriptions WHERE user_id = ?",
                    userId
            );
            jdbcTemplate.update(
                    "DELETE FROM subscription_purchase_intents WHERE user_id = ?",
                    userId
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
    void shouldPersistPendingIntentAndReturnBoundaryResult() {
        User user = saveUser();
        SubscriptionPlan plan = savePlan(true);
        saveOffer(plan);

        SubscriptionPurchaseIntentCreationResult result = service.prepare(
                new SubscriptionPurchaseIntentCreationCommand(
                        user.getId(),
                        plan.getCode(),
                        "TRIBUTE"
                )
        ).intentCreationResult();
        intentId = result.intentId();

        SubscriptionPurchaseIntent persisted = intentRepository
                .findById(intentId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(intentId, result.intentId()),
                () -> assertEquals("TRIBUTE", result.provider()),
                () -> assertEquals(EXPIRES_AT, result.expiresAt()),
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        persisted.getStatus()
                ),
                () -> assertEquals(
                        user.getId(),
                        persisted.getUser().getId()
                ),
                () -> assertEquals(
                        plan.getCode(),
                        persisted.getPlan().getCode()
                ),
                () -> assertEquals("TRIBUTE", persisted.getProvider()),
                () -> assertEquals(EXPIRES_AT, persisted.getExpiresAt()),
                () -> assertNull(persisted.getExternalCheckoutId()),
                () -> assertNull(persisted.getConsumedAt()),
                () -> assertNotNull(persisted.getCreatedAt()),
                () -> assertNotNull(persisted.getUpdatedAt())
        );
    }

    @Test
    void shouldPersistIntentAndPrepareCheckoutFromStoredOffer() {
        User user = saveUser();
        SubscriptionPlan plan = savePlan(true);
        PlanPaymentOffer offer = saveOffer(plan);

        SubscriptionPurchasePreparationResult result = service.prepare(
                new SubscriptionPurchaseIntentCreationCommand(
                        user.getId(),
                        plan.getCode(),
                        "TRIBUTE"
                )
        );
        intentId = result.intentCreationResult().intentId();

        SubscriptionPurchaseIntent persisted = intentRepository
                .findById(intentId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        intentId,
                        result.checkoutCommand().intentId()
                ),
                () -> assertEquals(
                        offer.getCode(),
                        result.checkoutCommand().offerCode()
                ),
                () -> assertEquals(
                        plan.getCode(),
                        result.checkoutCommand().planCode()
                ),
                () -> assertEquals(
                        plan.getDisplayName(),
                        result.checkoutCommand().planDisplayName()
                ),
                () -> assertEquals(
                        offer.getPriceMinor(),
                        result.checkoutCommand().priceMinor()
                ),
                () -> assertEquals(
                        offer.getCurrency(),
                        result.checkoutCommand().currency()
                ),
                () -> assertEquals(
                        offer.getBillingPeriod(),
                        result.checkoutCommand().billingPeriod()
                ),
                () -> assertEquals(
                        offer.getExternalProductId(),
                        result.checkoutCommand().externalProductId()
                ),
                () -> assertEquals(
                        EXPIRES_AT,
                        result.checkoutCommand().expiresAt()
                ),
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        persisted.getStatus()
                ),
                () -> assertEquals(
                        offer.getCode(),
                        persisted.getOffer().getCode()
                )
        );
    }

    @Test
    void shouldNotPersistIntentForMissingPlan() {
        User user = saveUser();

        assertThrows(
                PlanPaymentOfferNotFoundException.class,
                () -> service.prepare(
                        new SubscriptionPurchaseIntentCreationCommand(
                                user.getId(),
                                "MISSING_PLAN",
                                "TRIBUTE"
                        )
                )
        );

        assertEquals(0, countIntentsForUser(user.getId()));
    }

    @Test
    void shouldHideInactivePlanWithoutPersistingIntent() {
        User user = saveUser();
        SubscriptionPlan plan = savePlan(true);
        saveOffer(plan);
        plan.deactivate();
        planRepository.saveAndFlush(plan);

        assertThrows(
                PlanPaymentOfferNotFoundException.class,
                () -> service.prepare(
                        new SubscriptionPurchaseIntentCreationCommand(
                                user.getId(),
                                plan.getCode(),
                                "TRIBUTE"
                        )
                )
        );

        assertEquals(0, countIntentsForUser(user.getId()));
    }

    @Test
    void shouldRejectFreePlanWithoutPersistingIntent() {
        User user = saveUser();
        SubscriptionPlan freePlan = planRepository
                .findById("FREE")
                .orElseThrow();
        saveOffer(freePlan);

        assertThrows(
                PlanPaymentOfferNotFoundException.class,
                () -> service.prepare(
                        new SubscriptionPurchaseIntentCreationCommand(
                                user.getId(),
                                "FREE",
                                "TRIBUTE"
                        )
                )
        );

        assertEquals(0, countIntentsForUser(user.getId()));
    }

    @Test
    void shouldRejectPurchaseForUserWithEffectivePaidSubscription() {
        User user = saveUser();
        SubscriptionPlan plan = savePlan(true);
        saveOffer(plan);
        subscriptionRepository.saveAndFlush(UserSubscription.manual(
                user,
                plan,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600)
        ));

        assertThrows(
                SubscriptionPurchaseConflictException.class,
                () -> service.prepare(command(user, plan))
        );

        assertEquals(0, countIntentsForUser(user.getId()));
    }

    @Test
    void shouldReconcileExpiredSubscriptionBeforeNewPurchase() {
        User user = saveUser();
        SubscriptionPlan plan = savePlan(true);
        saveOffer(plan);
        UserSubscription subscription = subscriptionRepository.saveAndFlush(
                UserSubscription.manual(
                        user,
                        plan,
                        NOW.minusSeconds(3600),
                        NOW
                )
        );

        SubscriptionPurchasePreparationResult result = service.prepare(
                command(user, plan)
        );
        intentId = result.intentCreationResult().intentId();

        assertAll(
                () -> assertEquals(
                        SubscriptionStatus.EXPIRED,
                        subscriptionRepository.findById(subscription.getId())
                                .orElseThrow()
                                .getStatus()
                ),
                () -> assertEquals(1, countIntentsForUser(user.getId()))
        );
    }

    @Test
    void shouldCreateOnlyOneIntentForConcurrentRequests() throws Exception {
        User user = saveUser();
        SubscriptionPlan plan = savePlan(true);
        saveOffer(plan);
        SubscriptionPurchaseIntentCreationCommand command = command(user, plan);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> service.prepare(command)),
                    executor.submit(() -> service.prepare(command))
            );
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Конкурентные запросы не завершились вовремя"
                );
            }

            int successful = 0;
            int conflicts = 0;
            for (Future<Object> future : futures) {
                try {
                    future.get();
                    successful++;
                } catch (ExecutionException exception) {
                    if (exception.getCause()
                            instanceof SubscriptionPurchaseConflictException) {
                        conflicts++;
                    } else {
                        throw exception;
                    }
                }
            }

            int successfulRequests = successful;
            int conflictingRequests = conflicts;

            assertAll(
                    () -> assertEquals(1, successfulRequests),
                    () -> assertEquals(1, conflictingRequests),
                    () -> assertEquals(1, countIntentsForUser(user.getId()))
            );
        }
    }

    private SubscriptionPurchaseIntentCreationCommand command(
            User user,
            SubscriptionPlan plan
    ) {
        return new SubscriptionPurchaseIntentCreationCommand(
                user.getId(),
                plan.getCode(),
                "TRIBUTE"
        );
    }

    private User saveUser() {
        User user = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        userId = user.getId();
        return user;
    }

    private SubscriptionPlan savePlan(boolean active) {
        planCode = uniquePlanCode();
        SubscriptionPlan plan = new SubscriptionPlan(
                planCode,
                "Тестовый тариф"
        );
        if (!active) {
            plan.deactivate();
        }

        return planRepository.saveAndFlush(plan);
    }

    private PlanPaymentOffer saveOffer(SubscriptionPlan plan) {
        offerCode = uniqueOfferCode();
        return offerRepository.saveAndFlush(new PlanPaymentOffer(
                offerCode,
                plan,
                "TRIBUTE",
                99900,
                "RUB",
                BillingPeriod.MONTH,
                "product-" + UUID.randomUUID()
        ));
    }

    private Integer countIntentsForUser(UUID ownerId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM subscription_purchase_intents
                WHERE user_id = ?
                """,
                Integer.class,
                ownerId
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
