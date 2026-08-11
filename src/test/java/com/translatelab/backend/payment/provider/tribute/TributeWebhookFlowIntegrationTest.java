package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntentStatus;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.payment.tribute.enabled=true",
        "app.payment.tribute.api-key=synthetic-tribute-integration-key"
})
@AutoConfigureMockMvc
@Import(TributeWebhookFlowIntegrationTest.FixedClockConfiguration.class)
class TributeWebhookFlowIntegrationTest {

    private static final String PROVIDER = "TRIBUTE";
    private static final String API_KEY =
            "synthetic-tribute-integration-key";
    private static final String WEBHOOK_PATH =
            "/api/payments/webhooks/tribute";
    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );
    private static final Instant PERIOD_END = Instant.parse(
            "2026-10-01T00:00:00Z"
    );

    @Autowired
    private MockMvc mockMvc;

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
    private UUID orderId;
    private UUID subscriptionId;
    private String planCode;
    private String offerCode;

    @AfterEach
    void cleanUp() {
        if (orderId != null) {
            jdbcTemplate.update(
                    """
                    DELETE FROM processed_payment_events
                    WHERE provider = ?
                      AND external_event_id LIKE ?
                    """,
                    PROVIDER,
                    "%" + orderId + "%"
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
    void shouldProcessSignedCallbackOnceAndIgnoreDuplicateDelivery()
            throws Exception {
        savePendingIntent();
        byte[] rawBody = webhookBody();
        String signature = sign(rawBody);

        performWebhook(rawBody, signature);
        performWebhook(rawBody, signature);

        SubscriptionPurchaseIntent consumedIntent = intentRepository
                .findById(intentId)
                .orElseThrow();
        UserSubscription subscription = subscriptionRepository
                .findEffectiveActiveByUserIdAt(userId, NOW)
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.CONSUMED,
                        consumedIntent.getStatus()
                ),
                () -> assertEquals(NOW, consumedIntent.getConsumedAt()),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        subscription.getStatus()
                ),
                () -> assertEquals(
                        planCode,
                        subscription.getPlan().getCode()
                ),
                () -> assertEquals(
                        PROVIDER,
                        subscription.getProvider()
                ),
                () -> assertNull(subscription.getExternalCustomerId()),
                () -> assertEquals(
                        orderId.toString(),
                        subscription.getExternalOrderId()
                ),
                () -> assertNull(subscription.getExternalSubscriptionId()),
                () -> assertEquals(
                        NOW,
                        subscription.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        subscription.getCurrentPeriodEnd()
                ),
                () -> assertEquals(1, countStoredEvents()),
                () -> assertEquals(
                        "SUBSCRIPTION_PURCHASE_COMPLETED",
                        storedEventType()
                ),
                () -> assertEquals(1, countUserSubscriptions())
        );
    }

    @Test
    void shouldProcessRecurringPaymentLifecycleThroughWebhook()
            throws Exception {
        saveProviderManagedSubscription();

        performWebhook(
                lifecycleBody("shop_order_charge_failed", true),
                sign(lifecycleBody("shop_order_charge_failed", true))
        );
        assertEquals(
                SubscriptionStatus.PAST_DUE,
                loadSubscription().getStatus()
        );

        byte[] success = lifecycleBody("shop_order_charge_success", true);
        performWebhook(success, sign(success));
        UserSubscription recovered = loadSubscription();
        assertAll(
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        recovered.getStatus()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        recovered.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        Instant.parse("2026-11-01T00:00:00Z"),
                        recovered.getCurrentPeriodEnd()
                )
        );

        byte[] cancellation = lifecycleBody("shop_order_cancelled", true);
        performWebhook(cancellation, sign(cancellation));
        assertEquals(true, loadSubscription().isCancelAtPeriodEnd());

        byte[] refund = lifecycleBody("shop_order_refunded", false);
        performWebhook(refund, sign(refund));
        UserSubscription revoked = loadSubscription();
        assertAll(
                () -> assertEquals(
                        SubscriptionStatus.CANCELED,
                        revoked.getStatus()
                ),
                () -> assertEquals(
                        false,
                        revoked.isCancelAtPeriodEnd()
                ),
                () -> assertEquals(4, countLifecycleEvents())
        );
    }

    private void performWebhook(
            byte[] rawBody,
            String signature
    ) throws Exception {
        mockMvc.perform(
                        post(WEBHOOK_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawBody)
                                .header("trbt-signature", signature)
                )
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    private void savePendingIntent() {
        User user = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        userId = user.getId();

        planCode = uniqueCode("TEST_");
        SubscriptionPlan plan = planRepository.saveAndFlush(
                new SubscriptionPlan(planCode, "Тестовый тариф Tribute")
        );

        offerCode = uniqueCode("OFFER_");
        PlanPaymentOffer offer = new PlanPaymentOffer(
                offerCode,
                plan,
                PROVIDER,
                99_900,
                "RUB",
                BillingPeriod.MONTH,
                null
        );
        offerRepository.saveAndFlush(offer);

        orderId = UUID.randomUUID();
        SubscriptionPurchaseIntent intent =
                SubscriptionPurchaseIntent.pending(
                        user,
                        offer,
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(1800)
                );
        intent.attachCheckout(
                orderId.toString(),
                NOW.minusSeconds(30)
        );
        intentRepository.saveAndFlush(intent);
        intentId = intent.getId();
    }

    private void saveProviderManagedSubscription() {
        User user = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        userId = user.getId();

        planCode = uniqueCode("TEST_");
        SubscriptionPlan plan = planRepository.saveAndFlush(
                new SubscriptionPlan(planCode, "Тестовый тариф Tribute")
        );

        orderId = UUID.randomUUID();
        UserSubscription subscription =
                UserSubscription.providerManagedPurchase(
                        user,
                        plan,
                        NOW.minusSeconds(2_678_400),
                        PERIOD_END,
                        PROVIDER,
                        null,
                        orderId.toString(),
                        null,
                        99_900,
                        "RUB",
                        BillingPeriod.MONTH,
                        null
                );
        subscriptionRepository.saveAndFlush(subscription);
        subscriptionId = subscription.getId();
    }

    private byte[] webhookBody() {
        return ("""
                {
                  "name": "shop_order",
                  "created_at": "2026-09-01T00:00:00Z",
                  "sent_at": "2026-09-01T00:00:01Z",
                  "payload": {
                    "uuid": "%s",
                    "amount": 99900,
                    "currency": "rub",
                    "status": "paid",
                    "isRecurrent": false,
                    "period": "monthly"
                  }
                }
                """.formatted(orderId))
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] lifecycleBody(String name, boolean includePeriod) {
        String period = includePeriod
                ? ",\n                    \"period\": \"monthly\""
                : "";
        return ("""
                {
                  "name": "%s",
                  "created_at": "2026-09-01T00:00:00Z",
                  "sent_at": "2026-09-01T00:00:01Z",
                  "payload": {
                    "uuid": "%s",
                    "amount": 99900,
                    "currency": "rub"%s
                  }
                }
                """.formatted(name, orderId, period))
                .getBytes(StandardCharsets.UTF_8);
    }

    private String sign(byte[] rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(
                new SecretKeySpec(
                        API_KEY.getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256"
                )
        );
        return HexFormat.of().formatHex(mac.doFinal(rawBody));
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
                externalEventId()
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
                externalEventId()
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

    private UserSubscription loadSubscription() {
        return subscriptionRepository.findById(subscriptionId).orElseThrow();
    }

    private Integer countLifecycleEvents() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_payment_events
                WHERE provider = ?
                  AND external_event_id LIKE ?
                """,
                Integer.class,
                PROVIDER,
                "%" + orderId + "%"
        );
    }

    private String externalEventId() {
        return "shop_order:" + orderId;
    }

    private String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID()
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
