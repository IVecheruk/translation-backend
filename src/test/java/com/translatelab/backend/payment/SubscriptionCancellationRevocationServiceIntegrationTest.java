package com.translatelab.backend.payment;

import com.translatelab.backend.payment.dto.SubscriptionCancellationRevocationCommand;
import com.translatelab.backend.payment.service.SubscriptionCancellationRevocationService;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.subscription.entity.SubscriptionStatus;
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
class SubscriptionCancellationRevocationServiceIntegrationTest {

    private static final Instant PERIOD_START = Instant.parse(
            "2026-08-01T00:00:00Z"
    );
    private static final Instant PERIOD_END = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Autowired
    private SubscriptionCancellationRevocationService service;

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
    void shouldCommitRevocationOnceAndPreservePaidPeriod() {
        UserSubscription subscription = saveProviderSubscription(false);
        subscription.scheduleCancellationAtPeriodEnd();
        userSubscriptionRepository.saveAndFlush(subscription);
        externalEventId = uniqueExternalId("revocation-event");
        SubscriptionCancellationRevocationCommand command = command(
                externalEventId,
                subscription.getExternalSubscriptionId()
        );

        boolean firstProcessing = service.processCancellationRevocation(command);
        boolean duplicateProcessing = service.processCancellationRevocation(
                command
        );

        UserSubscription updated = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow();

        assertAll(
                () -> assertTrue(firstProcessing),
                () -> assertFalse(duplicateProcessing),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        updated.getStatus()
                ),
                () -> assertFalse(updated.isCancelAtPeriodEnd()),
                () -> assertEquals(PERIOD_START, updated.getCurrentPeriodStart()),
                () -> assertEquals(PERIOD_END, updated.getCurrentPeriodEnd()),
                () -> assertEquals(1, countStoredEvents(externalEventId)),
                () -> assertEquals(
                        "SUBSCRIPTION_CANCELLATION_REVOKED",
                        storedEventType(externalEventId)
                )
        );
    }

    @Test
    void shouldRollBackEventWhenSubscriptionDoesNotExist() {
        externalEventId = uniqueExternalId("missing-revocation-event");
        SubscriptionCancellationRevocationCommand command = command(
                externalEventId,
                uniqueExternalId("missing-subscription")
        );

        assertThrows(
                UserSubscriptionNotFoundException.class,
                () -> service.processCancellationRevocation(command)
        );

        assertEquals(0, countStoredEvents(externalEventId));
    }

    @Test
    void shouldRollBackEventWhenSubscriptionIsPastDue() {
        UserSubscription subscription = saveProviderSubscription(true);
        externalEventId = uniqueExternalId("invalid-revocation-event");
        SubscriptionCancellationRevocationCommand command = command(
                externalEventId,
                subscription.getExternalSubscriptionId()
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.processCancellationRevocation(command)
        );

        UserSubscription unchanged = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(0, countStoredEvents(externalEventId)),
                () -> assertEquals(
                        SubscriptionStatus.PAST_DUE,
                        unchanged.getStatus()
                ),
                () -> assertFalse(unchanged.isCancelAtPeriodEnd())
        );
    }

    private UserSubscription saveProviderSubscription(boolean pastDue) {
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

        UserSubscription subscription = UserSubscription.providerManaged(
                user,
                plan,
                PERIOD_START,
                PERIOD_END,
                "TRIBUTE",
                uniqueExternalId("customer"),
                uniqueExternalId("subscription")
        );
        if (pastDue) {
            subscription.markPastDue();
        }

        UserSubscription saved = userSubscriptionRepository.saveAndFlush(
                subscription
        );
        subscriptionId = saved.getId();
        return saved;
    }

    private SubscriptionCancellationRevocationCommand command(
            String eventId,
            String externalSubscriptionId
    ) {
        return new SubscriptionCancellationRevocationCommand(
                "TRIBUTE",
                eventId,
                externalSubscriptionId
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
