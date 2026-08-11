package com.translatelab.backend.subscription.entity;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSubscriptionTest {

    private static final Instant PERIOD_START = Instant.parse(
            "2026-08-01T00:00:00Z"
    );

    private static final Instant PERIOD_END = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Test
    void shouldCreateActiveManualSubscription() {
        User user = createUser();
        SubscriptionPlan plan = createPlan();

        UserSubscription subscription = UserSubscription.manual(
                user,
                plan,
                PERIOD_START,
                PERIOD_END
        );

        assertAll(
                () -> assertNull(subscription.getId()),
                () -> assertSame(user, subscription.getUser()),
                () -> assertSame(plan, subscription.getPlan()),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                ),
                () -> assertFalse(subscription.isCancelAtPeriodEnd()),
                () -> assertNull(subscription.getProvider()),
                () -> assertNull(subscription.getExternalCustomerId()),
                () -> assertNull(
                        subscription.getExternalSubscriptionId()
                ),
                () -> assertNull(subscription.getCreatedAt()),
                () -> assertNull(subscription.getUpdatedAt())
        );
    }

    @Test
    void shouldCreateProviderManagedSubscriptionAndNormalizeExternalIds() {
        User user = createUser();
        SubscriptionPlan plan = createPlan();

        UserSubscription subscription =
                UserSubscription.providerManaged(
                        user,
                        plan,
                        PERIOD_START,
                        PERIOD_END,
                        "TRIBUTE",
                        "  customer-123  ",
                        "  subscription-456  "
                );

        assertAll(
                () -> assertSame(user, subscription.getUser()),
                () -> assertSame(plan, subscription.getPlan()),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        "TRIBUTE",
                        subscription.getProvider()
                ),
                () -> assertEquals(
                        "customer-123",
                        subscription.getExternalCustomerId()
                ),
                () -> assertEquals(
                        "subscription-456",
                        subscription.getExternalSubscriptionId()
                )
        );
    }

    @Test
    void shouldAllowMissingExternalCustomerId() {
        UserSubscription subscription =
                UserSubscription.providerManaged(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END,
                        "TRIBUTE",
                        null,
                        "subscription-456"
                );

        assertAll(
                () -> assertNull(subscription.getExternalCustomerId()),
                () -> assertEquals(
                        "subscription-456",
                        subscription.getExternalSubscriptionId()
                )
        );
    }

    @Test
    void shouldScheduleCancellationAtPeriodEnd() {
        UserSubscription subscription = UserSubscription.manual(
                createUser(),
                createPlan(),
                PERIOD_START,
                PERIOD_END
        );

        subscription.scheduleCancellationAtPeriodEnd();

        assertAll(
                () -> assertTrue(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }

    @Test
    void shouldRevokeCancellationAtPeriodEnd() {
        UserSubscription subscription = UserSubscription.manual(
                createUser(),
                createPlan(),
                PERIOD_START,
                PERIOD_END
        );
        subscription.scheduleCancellationAtPeriodEnd();

        subscription.revokeCancellationAtPeriodEnd();

        assertAll(
                () -> assertFalse(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                )
        );
    }

    @Test
    void shouldKeepCancellationSchedulingIdempotent() {
        UserSubscription subscription = UserSubscription.manual(
                createUser(),
                createPlan(),
                PERIOD_START,
                PERIOD_END
        );

        subscription.scheduleCancellationAtPeriodEnd();
        subscription.scheduleCancellationAtPeriodEnd();

        assertTrue(subscription.isCancelAtPeriodEnd());

        subscription.revokeCancellationAtPeriodEnd();
        subscription.revokeCancellationAtPeriodEnd();

        assertFalse(subscription.isCancelAtPeriodEnd());
    }

    @Test
    void shouldMarkActiveSubscriptionPastDue() {
        UserSubscription subscription = createSubscription();
        subscription.scheduleCancellationAtPeriodEnd();

        subscription.markPastDue();

        assertAll(
                () -> assertEquals(
                        SubscriptionStatus.PAST_DUE,
                        subscription.getStatus()
                ),
                () -> assertFalse(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }

    @Test
    void shouldRejectRepeatedPastDueTransition() {
        UserSubscription subscription = createPastDueSubscription();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                subscription::markPastDue
        );

        assertAll(
                () -> assertEquals(
                        "Операция доступна только для активной подписки",
                        exception.getMessage()
                ),
                () -> assertPastDueWithOriginalPeriod(subscription)
        );
    }

    @Test
    void shouldRecoverPastDueSubscriptionAfterPayment() {
        UserSubscription subscription = createPastDueSubscription();
        Instant newPeriodStart = PERIOD_END;
        Instant newPeriodEnd = Instant.parse(
                "2026-10-01T00:00:00Z"
        );

        subscription.recoverAfterPayment(
                newPeriodStart,
                newPeriodEnd
        );

        assertAll(
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        newPeriodStart,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        newPeriodEnd,
                        subscription.getCurrentPeriodEnd()
                ),
                () -> assertFalse(
                        subscription.isCancelAtPeriodEnd()
                )
        );
    }

    @Test
    void shouldRejectRecoveryFromActiveSubscription() {
        UserSubscription subscription = createSubscription();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> subscription.recoverAfterPayment(
                        PERIOD_END,
                        Instant.parse("2026-10-01T00:00:00Z")
                )
        );

        assertAll(
                () -> assertEquals(
                        "Операция доступна только для подписки "
                                + "с просроченной оплатой",
                        exception.getMessage()
                ),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }

    @Test
    void shouldPreservePastDueStateWhenRecoveryStartIsMissing() {
        UserSubscription subscription = createPastDueSubscription();

        assertThrows(
                IllegalArgumentException.class,
                () -> subscription.recoverAfterPayment(
                        null,
                        PERIOD_END.plusSeconds(1)
                )
        );

        assertPastDueWithOriginalPeriod(subscription);
    }

    @Test
    void shouldPreservePastDueStateWhenRecoveryEndIsMissing() {
        UserSubscription subscription = createPastDueSubscription();

        assertThrows(
                IllegalArgumentException.class,
                () -> subscription.recoverAfterPayment(
                        PERIOD_END,
                        null
                )
        );

        assertPastDueWithOriginalPeriod(subscription);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void shouldPreservePastDueStateWhenRecoveryPeriodIsInvalid(
            long endOffsetSeconds
    ) {
        UserSubscription subscription = createPastDueSubscription();
        Instant newPeriodStart = PERIOD_END;

        assertThrows(
                IllegalArgumentException.class,
                () -> subscription.recoverAfterPayment(
                        newPeriodStart,
                        newPeriodStart.plusSeconds(endOffsetSeconds)
                )
        );

        assertPastDueWithOriginalPeriod(subscription);
    }

    @Test
    void shouldRejectCancellationChangesWhenPastDue() {
        UserSubscription subscription = createPastDueSubscription();

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        subscription::scheduleCancellationAtPeriodEnd
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        subscription::revokeCancellationAtPeriodEnd
                ),
                () -> assertPastDueWithOriginalPeriod(subscription)
        );
    }

    @Test
    void shouldCancelActiveSubscriptionImmediately() {
        UserSubscription subscription = createSubscription();
        subscription.scheduleCancellationAtPeriodEnd();

        subscription.cancelImmediately();

        assertTerminalWithOriginalPeriod(
                subscription,
                SubscriptionStatus.CANCELED
        );
    }

    @Test
    void shouldCancelPastDueSubscriptionImmediately() {
        UserSubscription subscription = createPastDueSubscription();

        subscription.cancelImmediately();

        assertTerminalWithOriginalPeriod(
                subscription,
                SubscriptionStatus.CANCELED
        );
    }

    @Test
    void shouldExpireActiveSubscriptionAtPeriodEnd() {
        UserSubscription subscription = createSubscription();
        subscription.scheduleCancellationAtPeriodEnd();

        subscription.expire(PERIOD_END);

        assertTerminalWithOriginalPeriod(
                subscription,
                SubscriptionStatus.EXPIRED
        );
    }

    @Test
    void shouldExpirePastDueSubscriptionAfterPeriodEnd() {
        UserSubscription subscription = createPastDueSubscription();

        subscription.expire(PERIOD_END.plusSeconds(1));

        assertTerminalWithOriginalPeriod(
                subscription,
                SubscriptionStatus.EXPIRED
        );
    }

    @Test
    void shouldRejectExpirationBeforePeriodEndAndPreserveState() {
        UserSubscription subscription = createSubscription();
        subscription.scheduleCancellationAtPeriodEnd();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> subscription.expire(
                        PERIOD_END.minusSeconds(1)
                )
        );

        assertAll(
                () -> assertEquals(
                        "Подписка не может истечь до окончания "
                                + "оплаченного периода",
                        exception.getMessage()
                ),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertTrue(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }

    @Test
    void shouldRejectMissingExpirationTimeAndPreserveState() {
        UserSubscription subscription = createSubscription();
        subscription.scheduleCancellationAtPeriodEnd();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> subscription.expire(null)
        );

        assertAll(
                () -> assertEquals(
                        "Момент окончания подписки не должен быть null",
                        exception.getMessage()
                ),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertTrue(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }

    @Test
    void shouldRejectTerminalOperationsAfterCancellation() {
        UserSubscription subscription = createSubscription();
        subscription.cancelImmediately();

        IllegalStateException cancellationException = assertThrows(
                IllegalStateException.class,
                subscription::cancelImmediately
        );
        IllegalStateException expirationException = assertThrows(
                IllegalStateException.class,
                () -> subscription.expire(PERIOD_END)
        );

        assertAll(
                () -> assertEquals(
                        "Операция недоступна для завершённой подписки",
                        cancellationException.getMessage()
                ),
                () -> assertEquals(
                        "Операция недоступна для завершённой подписки",
                        expirationException.getMessage()
                ),
                () -> assertTerminalWithOriginalPeriod(
                        subscription,
                        SubscriptionStatus.CANCELED
                )
        );
    }

    @Test
    void shouldRejectTerminalOperationsAfterExpiration() {
        UserSubscription subscription = createSubscription();
        subscription.expire(PERIOD_END);

        IllegalStateException cancellationException = assertThrows(
                IllegalStateException.class,
                subscription::cancelImmediately
        );
        IllegalStateException expirationException = assertThrows(
                IllegalStateException.class,
                () -> subscription.expire(PERIOD_END.plusSeconds(1))
        );

        assertAll(
                () -> assertEquals(
                        "Операция недоступна для завершённой подписки",
                        cancellationException.getMessage()
                ),
                () -> assertEquals(
                        "Операция недоступна для завершённой подписки",
                        expirationException.getMessage()
                ),
                () -> assertTerminalWithOriginalPeriod(
                        subscription,
                        SubscriptionStatus.EXPIRED
                )
        );
    }

    @Test
    void shouldRenewActiveSubscriptionWithContiguousPeriod() {
        UserSubscription subscription = createSubscription();
        Instant newPeriodEnd = Instant.parse(
                "2026-10-01T00:00:00Z"
        );

        subscription.renewPaidPeriod(
                PERIOD_END,
                newPeriodEnd
        );

        assertActiveWithPeriod(
                subscription,
                PERIOD_END,
                newPeriodEnd
        );
    }

    @Test
    void shouldRejectRenewalWithScheduledCancellation() {
        UserSubscription subscription = createSubscription();
        subscription.scheduleCancellationAtPeriodEnd();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> subscription.renewPaidPeriod(
                        PERIOD_END,
                        Instant.parse("2026-10-01T00:00:00Z")
                )
        );

        assertAll(
                () -> assertEquals(
                        "Нельзя продлить подписку "
                                + "с запланированной отменой",
                        exception.getMessage()
                ),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertTrue(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1})
    void shouldRejectNonContiguousRenewalStart(
            long startOffsetSeconds
    ) {
        UserSubscription subscription = createSubscription();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> subscription.renewPaidPeriod(
                        PERIOD_END.plusSeconds(startOffsetSeconds),
                        Instant.parse("2026-10-01T00:00:00Z")
                )
        );

        assertAll(
                () -> assertEquals(
                        "Новый период должен начинаться "
                                + "в момент окончания текущего периода",
                        exception.getMessage()
                ),
                () -> assertActiveWithPeriod(
                        subscription,
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldPreserveStateWhenRenewalStartIsMissing() {
        UserSubscription subscription = createSubscription();

        assertThrows(
                IllegalArgumentException.class,
                () -> subscription.renewPaidPeriod(
                        null,
                        Instant.parse("2026-10-01T00:00:00Z")
                )
        );

        assertActiveWithPeriod(
                subscription,
                PERIOD_START,
                PERIOD_END
        );
    }

    @Test
    void shouldPreserveStateWhenRenewalEndIsMissing() {
        UserSubscription subscription = createSubscription();

        assertThrows(
                IllegalArgumentException.class,
                () -> subscription.renewPaidPeriod(
                        PERIOD_END,
                        null
                )
        );

        assertActiveWithPeriod(
                subscription,
                PERIOD_START,
                PERIOD_END
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void shouldPreserveStateWhenRenewalEndIsInvalid(
            long endOffsetSeconds
    ) {
        UserSubscription subscription = createSubscription();

        assertThrows(
                IllegalArgumentException.class,
                () -> subscription.renewPaidPeriod(
                        PERIOD_END,
                        PERIOD_END.plusSeconds(endOffsetSeconds)
                )
        );

        assertActiveWithPeriod(
                subscription,
                PERIOD_START,
                PERIOD_END
        );
    }

    @Test
    void shouldRejectRenewalWhenSubscriptionIsNotActive() {
        UserSubscription pastDue = createPastDueSubscription();
        UserSubscription canceled = createSubscription();
        canceled.cancelImmediately();
        UserSubscription expired = createSubscription();
        expired.expire(PERIOD_END);
        Instant newPeriodEnd = Instant.parse(
                "2026-10-01T00:00:00Z"
        );

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> pastDue.renewPaidPeriod(
                                PERIOD_END,
                                newPeriodEnd
                        )
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> canceled.renewPaidPeriod(
                                PERIOD_END,
                                newPeriodEnd
                        )
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> expired.renewPaidPeriod(
                                PERIOD_END,
                                newPeriodEnd
                        )
                ),
                () -> assertPastDueWithOriginalPeriod(pastDue),
                () -> assertTerminalWithOriginalPeriod(
                        canceled,
                        SubscriptionStatus.CANCELED
                ),
                () -> assertTerminalWithOriginalPeriod(
                        expired,
                        SubscriptionStatus.EXPIRED
                )
        );
    }

    @Test
    void shouldRejectRepeatedRenewalAndPreserveRenewedPeriod() {
        UserSubscription subscription = createSubscription();
        Instant newPeriodEnd = Instant.parse(
                "2026-10-01T00:00:00Z"
        );
        subscription.renewPaidPeriod(PERIOD_END, newPeriodEnd);

        assertThrows(
                IllegalStateException.class,
                () -> subscription.renewPaidPeriod(
                        PERIOD_END,
                        newPeriodEnd
                )
        );

        assertActiveWithPeriod(
                subscription,
                PERIOD_END,
                newPeriodEnd
        );
    }

    @Test
    void shouldRejectMissingUser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.manual(
                        null,
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectMissingPlan() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.manual(
                        createUser(),
                        null,
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectInactivePlan() {
        SubscriptionPlan plan = createPlan();
        plan.deactivate();

        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.manual(
                        createUser(),
                        plan,
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectMissingPeriodStart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.manual(
                        createUser(),
                        createPlan(),
                        null,
                        PERIOD_END
                )
        );
    }

    @Test
    void shouldRejectMissingPeriodEnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.manual(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        null
                )
        );
    }

    @Test
    void shouldRejectPeriodEndEqualToStart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.manual(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_START
                )
        );
    }

    @Test
    void shouldRejectPeriodEndBeforeStart() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.manual(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_START.minusSeconds(1)
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "PAYMENT-PROVIDER",
            "PAYMENT PROVIDER",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567"
    })
    void shouldRejectInvalidProvider(String provider) {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.providerManaged(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END,
                        provider,
                        null,
                        "subscription-456"
                )
        );
    }

    @Test
    void shouldAcceptMaximumLengthProvider() {
        String provider = "A".repeat(32);

        UserSubscription subscription =
                UserSubscription.providerManaged(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END,
                        provider,
                        null,
                        "subscription-456"
                );

        assertEquals(provider, subscription.getProvider());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectMissingExternalSubscriptionId(
            String externalSubscriptionId
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.providerManaged(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END,
                        "TRIBUTE",
                        null,
                        externalSubscriptionId
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   "})
    void shouldRejectBlankExternalCustomerId(
            String externalCustomerId
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.providerManaged(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END,
                        "TRIBUTE",
                        externalCustomerId,
                        "subscription-456"
                )
        );
    }

    @Test
    void shouldRejectTooLongExternalCustomerId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.providerManaged(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END,
                        "TRIBUTE",
                        "a".repeat(256),
                        "subscription-456"
                )
        );
    }

    @Test
    void shouldRejectTooLongExternalSubscriptionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UserSubscription.providerManaged(
                        createUser(),
                        createPlan(),
                        PERIOD_START,
                        PERIOD_END,
                        "TRIBUTE",
                        null,
                        "a".repeat(256)
                )
        );
    }

    private User createUser() {
        return new User(
                "user@example.com",
                "password-hash"
        );
    }

    private SubscriptionPlan createPlan() {
        return new SubscriptionPlan(
                "PRO",
                "Профессиональный"
        );
    }

    private UserSubscription createSubscription() {
        return UserSubscription.manual(
                createUser(),
                createPlan(),
                PERIOD_START,
                PERIOD_END
        );
    }

    private UserSubscription createPastDueSubscription() {
        UserSubscription subscription = createSubscription();
        subscription.markPastDue();
        return subscription;
    }

    private void assertPastDueWithOriginalPeriod(
            UserSubscription subscription
    ) {
        assertAll(
                () -> assertEquals(
                        SubscriptionStatus.PAST_DUE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                ),
                () -> assertFalse(
                        subscription.isCancelAtPeriodEnd()
                )
        );
    }

    private void assertTerminalWithOriginalPeriod(
            UserSubscription subscription,
            SubscriptionStatus expectedStatus
    ) {
        assertAll(
                () -> assertEquals(
                        expectedStatus,
                        subscription.getStatus()
                ),
                () -> assertFalse(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }

    private void assertActiveWithPeriod(
            UserSubscription subscription,
            Instant expectedPeriodStart,
            Instant expectedPeriodEnd
    ) {
        assertAll(
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertFalse(
                        subscription.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(
                        expectedPeriodStart,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        expectedPeriodEnd,
                        subscription.getCurrentPeriodEnd()
                )
        );
    }
}
