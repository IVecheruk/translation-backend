package com.translatelab.backend.payment.entity;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SubscriptionPurchaseIntentTest {

    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Instant EXPIRES_AT = NOW.plusSeconds(1800);

    private final User user = new User(
            "user@example.com",
            "password-hash"
    );
    private final SubscriptionPlan plan = new SubscriptionPlan(
            "PRO",
            "Профессиональный"
    );
    private final PlanPaymentOffer offer = offer("TRIBUTE");

    @Test
    void shouldCreatePendingIntent() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        assertAll(
                () -> assertSame(user, intent.getUser()),
                () -> assertSame(plan, intent.getPlan()),
                () -> assertSame(offer, intent.getOffer()),
                () -> assertEquals("TRIBUTE", intent.getProvider()),
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        intent.getStatus()
                ),
                () -> assertEquals(EXPIRES_AT, intent.getExpiresAt()),
                () -> assertNull(intent.getExternalCheckoutId()),
                () -> assertNull(intent.getConsumedAt()),
                () -> assertNull(intent.getId()),
                () -> assertNull(intent.getCreatedAt()),
                () -> assertNull(intent.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectMissingUser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        null,
                        offer,
                        NOW,
                        EXPIRES_AT
                )
        );
    }

    @Test
    void shouldRejectMissingOffer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        null,
                        NOW,
                        EXPIRES_AT
                )
        );
    }

    @Test
    void shouldRejectOfferWithoutPlan() {
        PlanPaymentOffer offerWithoutPlan = mock(PlanPaymentOffer.class);
        given(offerWithoutPlan.isActive()).willReturn(true);
        given(offerWithoutPlan.getPlan()).willReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        offerWithoutPlan,
                        NOW,
                        EXPIRES_AT
                )
        );
    }

    @Test
    void shouldRejectInactivePlan() {
        plan.deactivate();

        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        NOW,
                        EXPIRES_AT
                )
        );
    }

    @Test
    void shouldRejectInactiveOffer() {
        offer.deactivate();

        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        NOW,
                        EXPIRES_AT
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "TRIBUTE_1",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidProviderBoundaries(String provider) {
        SubscriptionPurchaseIntent intent =
                SubscriptionPurchaseIntent.pending(
                        user,
                        offer(provider),
                        NOW,
                        EXPIRES_AT
                );

        assertEquals(provider, intent.getProvider());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "tribute",
            "1TRIBUTE",
            "TRIBUTE-PAY",
            "TRIBUTE PAY",
            "A12345678901234567890123456789012"
    })
    void shouldRejectInvalidProvider(String provider) {
        PlanPaymentOffer invalidOffer = mock(PlanPaymentOffer.class);
        given(invalidOffer.isActive()).willReturn(true);
        given(invalidOffer.getPlan()).willReturn(plan);
        given(invalidOffer.getProvider()).willReturn(provider);

        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        invalidOffer,
                        NOW,
                        EXPIRES_AT
                )
        );
    }

    @Test
    void shouldRejectMissingCurrentTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        null,
                        EXPIRES_AT
                )
        );
    }

    @Test
    void shouldRejectMissingExpirationTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        NOW,
                        null
                )
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void shouldRejectExpirationNotAfterCurrentTime(long offsetSeconds) {
        assertThrows(
                IllegalArgumentException.class,
                () -> SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        NOW,
                        NOW.plusSeconds(offsetSeconds)
                )
        );
    }

    @Test
    void shouldAttachNormalizedCheckout() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        intent.attachCheckout("  checkout-123  ", NOW);

        assertEquals("checkout-123", intent.getExternalCheckoutId());
    }

    @Test
    void shouldTreatSameCheckoutAttachmentAsIdempotent() {
        SubscriptionPurchaseIntent intent = pendingIntent();
        intent.attachCheckout("checkout-123", NOW);

        intent.attachCheckout("  checkout-123  ", NOW.plusSeconds(1));

        assertEquals("checkout-123", intent.getExternalCheckoutId());
    }

    @Test
    void shouldRejectReplacingAttachedCheckout() {
        SubscriptionPurchaseIntent intent = pendingIntent();
        intent.attachCheckout("checkout-123", NOW);

        assertThrows(
                IllegalStateException.class,
                () -> intent.attachCheckout(
                        "checkout-456",
                        NOW.plusSeconds(1)
                )
        );

        assertEquals("checkout-123", intent.getExternalCheckoutId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectMissingCheckoutId(String checkoutId) {
        SubscriptionPurchaseIntent intent = pendingIntent();

        assertThrows(
                IllegalArgumentException.class,
                () -> intent.attachCheckout(checkoutId, NOW)
        );

        assertNull(intent.getExternalCheckoutId());
    }

    @Test
    void shouldRejectCheckoutIdLongerThanDatabaseColumn() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        assertThrows(
                IllegalArgumentException.class,
                () -> intent.attachCheckout("x".repeat(256), NOW)
        );

        assertNull(intent.getExternalCheckoutId());
    }

    @Test
    void shouldRejectCheckoutAttachmentAtExactExpiration() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        assertThrows(
                IllegalStateException.class,
                () -> intent.attachCheckout("checkout-123", EXPIRES_AT)
        );

        assertNull(intent.getExternalCheckoutId());
    }

    @Test
    void shouldConsumeUsableIntentWithAttachedCheckout() {
        SubscriptionPurchaseIntent intent = pendingIntent();
        Instant consumedAt = NOW.plusSeconds(60);
        intent.attachCheckout("checkout-123", NOW);

        intent.consume(consumedAt);

        assertAll(
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.CONSUMED,
                        intent.getStatus()
                ),
                () -> assertEquals(consumedAt, intent.getConsumedAt()),
                () -> assertEquals(
                        "checkout-123",
                        intent.getExternalCheckoutId()
                )
        );
    }

    @Test
    void shouldRejectConsumptionWithoutCheckout() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        assertThrows(
                IllegalStateException.class,
                () -> intent.consume(NOW)
        );

        assertAll(
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        intent.getStatus()
                ),
                () -> assertNull(intent.getConsumedAt())
        );
    }

    @Test
    void shouldRejectConsumptionAtExactExpiration() {
        SubscriptionPurchaseIntent intent = pendingIntent();
        intent.attachCheckout("checkout-123", NOW);

        assertThrows(
                IllegalStateException.class,
                () -> intent.consume(EXPIRES_AT)
        );

        assertAll(
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        intent.getStatus()
                ),
                () -> assertNull(intent.getConsumedAt())
        );
    }

    @Test
    void shouldExpireAtExactBoundary() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        intent.expire(EXPIRES_AT);

        assertAll(
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.EXPIRED,
                        intent.getStatus()
                ),
                () -> assertNull(intent.getConsumedAt())
        );
    }

    @Test
    void shouldRejectExpirationBeforeBoundary() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        assertThrows(
                IllegalStateException.class,
                () -> intent.expire(EXPIRES_AT.minusNanos(1))
        );

        assertEquals(
                SubscriptionPurchaseIntentStatus.PENDING,
                intent.getStatus()
        );
    }

    @Test
    void shouldCancelPendingIntent() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        intent.cancel();

        assertAll(
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.CANCELED,
                        intent.getStatus()
                ),
                () -> assertNull(intent.getConsumedAt())
        );
    }

    @Test
    void shouldRejectChangesAfterConsumption() {
        SubscriptionPurchaseIntent intent = pendingIntent();
        intent.attachCheckout("checkout-123", NOW);
        intent.consume(NOW.plusSeconds(1));

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> intent.attachCheckout(
                                "checkout-123",
                                NOW.plusSeconds(2)
                        )
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> intent.consume(NOW.plusSeconds(2))
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> intent.expire(EXPIRES_AT)
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        intent::cancel
                )
        );
    }

    @Test
    void shouldRejectNullTimesInLifecycleOperations() {
        SubscriptionPurchaseIntent intent = pendingIntent();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> intent.attachCheckout("checkout-123", null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> intent.consume(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> intent.expire(null)
                )
        );
    }

    private SubscriptionPurchaseIntent pendingIntent() {
        return SubscriptionPurchaseIntent.pending(
                user,
                offer,
                NOW,
                EXPIRES_AT
        );
    }

    private PlanPaymentOffer offer(String provider) {
        return new PlanPaymentOffer(
                "OFFER_" + provider,
                plan,
                provider,
                99900,
                "RUB",
                BillingPeriod.MONTH,
                null
        );
    }
}
