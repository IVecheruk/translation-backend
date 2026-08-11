package com.translatelab.backend.payment.entity;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanPaymentOfferTest {

    private final SubscriptionPlan plan = new SubscriptionPlan(
            "PRO",
            "Профессиональный"
    );

    @Test
    void shouldCreateActiveOffer() {
        PlanPaymentOffer offer = offer("product-123");

        assertAll(
                () -> assertEquals("PRO_TRIBUTE_MONTH", offer.getCode()),
                () -> assertSame(plan, offer.getPlan()),
                () -> assertEquals("TRIBUTE", offer.getProvider()),
                () -> assertEquals(99900L, offer.getPriceMinor()),
                () -> assertEquals("RUB", offer.getCurrency()),
                () -> assertEquals(
                        BillingPeriod.MONTH,
                        offer.getBillingPeriod()
                ),
                () -> assertEquals(
                        "product-123",
                        offer.getExternalProductId()
                ),
                () -> assertTrue(offer.isActive()),
                () -> assertNull(offer.getCreatedAt()),
                () -> assertNull(offer.getUpdatedAt())
        );
    }

    @Test
    void shouldAcceptCodeAtMaximumLength() {
        String code = "A" + "1".repeat(63);

        PlanPaymentOffer offer = new PlanPaymentOffer(
                code,
                plan,
                "TRIBUTE",
                99900,
                "RUB",
                BillingPeriod.MONTH,
                null
        );

        assertEquals(code, offer.getCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "pro_offer",
            "1PRO_OFFER",
            "PRO-OFFER",
            "A1234567890123456789012345678901234567890123456789012345678901234"
    })
    void shouldRejectInvalidCode(String code) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanPaymentOffer(
                        code,
                        plan,
                        "TRIBUTE",
                        99900,
                        "RUB",
                        BillingPeriod.MONTH,
                        null
                )
        );
    }

    @Test
    void shouldRejectMissingPlan() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanPaymentOffer(
                        "PRO_TRIBUTE_MONTH",
                        null,
                        "TRIBUTE",
                        99900,
                        "RUB",
                        BillingPeriod.MONTH,
                        null
                )
        );
    }

    @Test
    void shouldRejectInactivePlan() {
        plan.deactivate();

        assertThrows(
                IllegalArgumentException.class,
                () -> offer(null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "TRIBUTE_1",
            "A1234567890123456789012345678901"
    })
    void shouldAcceptValidProviderBoundaries(String provider) {
        PlanPaymentOffer offer = new PlanPaymentOffer(
                "PRO_TRIBUTE_MONTH",
                plan,
                provider,
                99900,
                "RUB",
                BillingPeriod.MONTH,
                null
        );

        assertEquals(provider, offer.getProvider());
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
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanPaymentOffer(
                        "PRO_TRIBUTE_MONTH",
                        plan,
                        provider,
                        99900,
                        "RUB",
                        BillingPeriod.MONTH,
                        null
                )
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0})
    void shouldRejectNonPositivePrice(long priceMinor) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanPaymentOffer(
                        "PRO_TRIBUTE_MONTH",
                        plan,
                        "TRIBUTE",
                        priceMinor,
                        "RUB",
                        BillingPeriod.MONTH,
                        null
                )
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "rub", "RU", "RUB1", "R1B"})
    void shouldRejectInvalidCurrency(String currency) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanPaymentOffer(
                        "PRO_TRIBUTE_MONTH",
                        plan,
                        "TRIBUTE",
                        99900,
                        currency,
                        BillingPeriod.MONTH,
                        null
                )
        );
    }

    @Test
    void shouldRejectMissingBillingPeriod() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlanPaymentOffer(
                        "PRO_TRIBUTE_MONTH",
                        plan,
                        "TRIBUTE",
                        99900,
                        "RUB",
                        null,
                        null
                )
        );
    }

    @Test
    void shouldAcceptMissingExternalProductId() {
        PlanPaymentOffer offer = offer(null);

        assertNull(offer.getExternalProductId());
    }

    @Test
    void shouldNormalizeExternalProductId() {
        PlanPaymentOffer offer = offer("  product-123  ");

        assertEquals("product-123", offer.getExternalProductId());
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectBlankExternalProductId(String externalProductId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> offer(externalProductId)
        );
    }

    @Test
    void shouldRejectExternalProductIdLongerThanColumn() {
        assertThrows(
                IllegalArgumentException.class,
                () -> offer("x".repeat(256))
        );
    }

    @Test
    void shouldDeactivateAndReactivateOffer() {
        PlanPaymentOffer offer = offer(null);

        offer.deactivate();
        assertFalse(offer.isActive());

        offer.activate();
        assertTrue(offer.isActive());
    }

    @Test
    void shouldRejectActivationForInactivePlan() {
        PlanPaymentOffer offer = offer(null);
        offer.deactivate();
        plan.deactivate();

        assertThrows(IllegalStateException.class, offer::activate);
        assertFalse(offer.isActive());
    }

    private PlanPaymentOffer offer(String externalProductId) {
        return new PlanPaymentOffer(
                "PRO_TRIBUTE_MONTH",
                plan,
                "TRIBUTE",
                99900,
                "RUB",
                BillingPeriod.MONTH,
                externalProductId
        );
    }
}
