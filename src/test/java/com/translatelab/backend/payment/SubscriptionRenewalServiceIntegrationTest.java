package com.translatelab.backend.payment;

import com.translatelab.backend.payment.dto.SubscriptionRenewalCommand;
import com.translatelab.backend.payment.service.SubscriptionRenewalService;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.exception.UserSubscriptionNotFoundException;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SubscriptionRenewalServiceIntegrationTest {

    private static final Instant CURRENT_PERIOD_START = Instant.parse(
            "2026-08-01T00:00:00Z"
    );
    private static final Instant CURRENT_PERIOD_END = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Instant RENEWED_PERIOD_END = Instant.parse(
            "2026-10-01T00:00:00Z"
    );

    @Autowired
    private SubscriptionRenewalService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID subscriptionId;
    private String planCode;
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
                    "TRIBUTE",
                    externalEventId
            );
        }

        if (subscriptionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM user_subscriptions WHERE id = ?",
                    subscriptionId
            );
        }

        if (planCode != null) {
            jdbcTemplate.update(
                    "DELETE FROM subscription_plans WHERE code = ?",
                    planCode
            );
        }

        if (userId != null) {
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    userId
            );
        }
    }

    @Test
    void shouldCommitRenewalOnceAndIgnoreDuplicate() {
        UserSubscription subscription = saveProviderSubscription();
        externalEventId = uniqueExternalId("event");
        SubscriptionRenewalCommand command = renewalCommand(
                externalEventId,
                subscription.getExternalSubscriptionId(),
                CURRENT_PERIOD_END,
                RENEWED_PERIOD_END
        );

        boolean firstProcessing = service.processRenewal(command);
        boolean duplicateProcessing = service.processRenewal(command);

        UserSubscription renewed = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow();
        Integer storedEvents = countStoredEvents(externalEventId);

        assertAll(
                () -> assertTrue(firstProcessing),
                () -> assertFalse(duplicateProcessing),
                () -> assertEquals(
                        CURRENT_PERIOD_END,
                        renewed.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        RENEWED_PERIOD_END,
                        renewed.getCurrentPeriodEnd()
                ),
                () -> assertEquals(1, storedEvents),
                () -> assertEquals(
                        "SUBSCRIPTION_RENEWED",
                        storedEventType(externalEventId)
                )
        );
    }

    @Test
    void shouldRollBackEventWhenSubscriptionDoesNotExist() {
        externalEventId = uniqueExternalId("missing-event");
        SubscriptionRenewalCommand command = renewalCommand(
                externalEventId,
                uniqueExternalId("missing-subscription"),
                CURRENT_PERIOD_END,
                RENEWED_PERIOD_END
        );

        assertThrows(
                UserSubscriptionNotFoundException.class,
                () -> service.processRenewal(command)
        );

        assertEquals(0, countStoredEvents(externalEventId));
    }

    @Test
    void shouldRollBackEventAndSubscriptionOnDomainFailure() {
        UserSubscription subscription = saveProviderSubscription();
        externalEventId = uniqueExternalId("invalid-period-event");
        SubscriptionRenewalCommand command = renewalCommand(
                externalEventId,
                subscription.getExternalSubscriptionId(),
                CURRENT_PERIOD_END.plusSeconds(1),
                RENEWED_PERIOD_END
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.processRenewal(command)
        );

        UserSubscription unchanged = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(0, countStoredEvents(externalEventId)),
                () -> assertEquals(
                        CURRENT_PERIOD_START,
                        unchanged.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        CURRENT_PERIOD_END,
                        unchanged.getCurrentPeriodEnd()
                )
        );
    }

    private UserSubscription saveProviderSubscription() {
        User user = userRepository.save(
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
        SubscriptionPlan plan = subscriptionPlanRepository.save(
                new SubscriptionPlan(planCode, "Тестовый тариф")
        );

        UserSubscription subscription = userSubscriptionRepository
                .saveAndFlush(
                        UserSubscription.providerManaged(
                                user,
                                plan,
                                CURRENT_PERIOD_START,
                                CURRENT_PERIOD_END,
                                "TRIBUTE",
                                uniqueExternalId("customer"),
                                uniqueExternalId("subscription")
                        )
                );
        subscriptionId = subscription.getId();
        return subscription;
    }

    private SubscriptionRenewalCommand renewalCommand(
            String eventId,
            String externalSubscriptionId,
            Instant newPeriodStart,
            Instant newPeriodEnd
    ) {
        return new SubscriptionRenewalCommand(
                "TRIBUTE",
                eventId,
                externalSubscriptionId,
                newPeriodStart,
                newPeriodEnd
        );
    }

    private Integer countStoredEvents(String eventId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_payment_events
                WHERE provider = ?
                  AND external_event_id = ?
                """,
                Integer.class,
                "TRIBUTE",
                eventId
        );
    }

    private String storedEventType(String eventId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT event_type
                FROM processed_payment_events
                WHERE provider = ?
                  AND external_event_id = ?
                """,
                String.class,
                "TRIBUTE",
                eventId
        );
    }

    private String uniqueExternalId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
