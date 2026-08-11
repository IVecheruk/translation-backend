package com.translatelab.backend.payment.entity;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionPurchaseIntentCommercialSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    @Test
    void shouldAcceptOnlyExactImmutableCommercialSnapshot() {
        SubscriptionPlan plan = new SubscriptionPlan("PRO", "Pro");
        PlanPaymentOffer offer = new PlanPaymentOffer(
                "PRO_TRIBUTE_MONTH",
                plan,
                "TRIBUTE",
                49_900,
                "RUB",
                BillingPeriod.MONTH,
                "product-pro"
        );
        SubscriptionPurchaseIntent intent = SubscriptionPurchaseIntent.pending(
                new User("snapshot@example.com", "password-hash"),
                offer,
                NOW,
                NOW.plusSeconds(1800)
        );

        assertTrue(intent.matchesCommercialSnapshot(
                49_900,
                "RUB",
                BillingPeriod.MONTH,
                "product-pro"
        ));
        assertFalse(intent.matchesCommercialSnapshot(
                49_899,
                "RUB",
                BillingPeriod.MONTH,
                "product-pro"
        ));
        assertFalse(intent.matchesCommercialSnapshot(
                49_900,
                "USD",
                BillingPeriod.MONTH,
                "product-pro"
        ));
        assertFalse(intent.matchesCommercialSnapshot(
                49_900,
                "RUB",
                BillingPeriod.MONTH,
                "another-product"
        ));
    }
}
