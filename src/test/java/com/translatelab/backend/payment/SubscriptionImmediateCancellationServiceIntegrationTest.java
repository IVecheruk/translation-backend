package com.translatelab.backend.payment;

import com.translatelab.backend.payment.dto.SubscriptionImmediateCancellationCommand;
import com.translatelab.backend.payment.service.SubscriptionImmediateCancellationService;
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
class SubscriptionImmediateCancellationServiceIntegrationTest {

    private static final Instant PERIOD_START = Instant.parse(
            "2026-08-01T00:00:00Z"
    );
    private static final Instant PERIOD_END = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Autowired
    private SubscriptionImmediateCancellationService service;

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
    void shouldCommitCancellationFromActiveOnceAndRevokeEntitlement() {
        UserSubscription subscription = saveProviderSubscription(
                SubscriptionStatus.ACTIVE
        );
        subscription.scheduleCancellationAtPeriodEnd();
        userSubscriptionRepository.saveAndFlush(subscription);
        externalEventId = uniqueExternalId("cancellation-event");
        SubscriptionImmediateCancellationCommand command = command(
                externalEventId,
                subscription.getExternalSubscriptionId()
        );

        boolean firstProcessing = service.processImmediateCancellation(command);
        boolean duplicateProcessing = service.processImmediateCancellation(
                command
        );

        UserSubscription canceled = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow();
        boolean remainsEffective = userSubscriptionRepository
                .findEffectiveActiveByUserIdAt(
                        userId,
                        PERIOD_START.plusSeconds(1)
                )
                .isPresent();

        assertAll(
                () -> assertTrue(firstProcessing),
                () -> assertFalse(duplicateProcessing),
                () -> assertEquals(
                        SubscriptionStatus.CANCELED,
                        canceled.getStatus()
                ),
                () -> assertFalse(canceled.isCancelAtPeriodEnd()),
                () -> assertFalse(remainsEffective),
                () -> assertEquals(PERIOD_START, canceled.getCurrentPeriodStart()),
                () -> assertEquals(PERIOD_END, canceled.getCurrentPeriodEnd()),
                () -> assertEquals(1, countStoredEvents(externalEventId)),
                () -> assertEquals(
                        "SUBSCRIPTION_CANCELED_IMMEDIATELY",
                        storedEventType(externalEventId)
                )
        );
    }

    @Test
    void shouldCommitCancellationFromPastDue() {
        UserSubscription subscription = saveProviderSubscription(
                SubscriptionStatus.PAST_DUE
        );
        externalEventId = uniqueExternalId("past-due-cancellation-event");

        boolean processed = service.processImmediateCancellation(
                command(
                        externalEventId,
                        subscription.getExternalSubscriptionId()
                )
        );

        UserSubscription canceled = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow();

        assertAll(
                () -> assertTrue(processed),
                () -> assertEquals(
                        SubscriptionStatus.CANCELED,
                        canceled.getStatus()
                ),
                () -> assertEquals(1, countStoredEvents(externalEventId))
        );
    }

    @Test
    void shouldRollBackEventWhenSubscriptionDoesNotExist() {
        externalEventId = uniqueExternalId("missing-cancellation-event");
        SubscriptionImmediateCancellationCommand command = command(
                externalEventId,
                uniqueExternalId("missing-subscription")
        );

        assertThrows(
                UserSubscriptionNotFoundException.class,
                () -> service.processImmediateCancellation(command)
        );

        assertEquals(0, countStoredEvents(externalEventId));
    }

    @Test
    void shouldRollBackEventWhenSubscriptionIsAlreadyCanceled() {
        UserSubscription subscription = saveProviderSubscription(
                SubscriptionStatus.CANCELED
        );
        externalEventId = uniqueExternalId("repeated-cancellation-event");
        SubscriptionImmediateCancellationCommand command = command(
                externalEventId,
                subscription.getExternalSubscriptionId()
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.processImmediateCancellation(command)
        );

        UserSubscription unchanged = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(0, countStoredEvents(externalEventId)),
                () -> assertEquals(
                        SubscriptionStatus.CANCELED,
                        unchanged.getStatus()
                )
        );
    }

    private UserSubscription saveProviderSubscription(
            SubscriptionStatus initialStatus
    ) {
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
        if (initialStatus == SubscriptionStatus.PAST_DUE) {
            subscription.markPastDue();
        } else if (initialStatus == SubscriptionStatus.CANCELED) {
            subscription.cancelImmediately();
        }

        UserSubscription saved = userSubscriptionRepository.saveAndFlush(
                subscription
        );
        subscriptionId = saved.getId();
        return saved;
    }

    private SubscriptionImmediateCancellationCommand command(
            String eventId,
            String externalSubscriptionId
    ) {
        return new SubscriptionImmediateCancellationCommand(
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
