package com.translatelab.backend.payment;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntentStatus;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SubscriptionPurchaseIntentRepositoryIntegrationTest {

    @Autowired
    private SubscriptionPurchaseIntentRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private PlanPaymentOfferRepository offerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAndScopeLockedLookups() {
        User owner = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        User anotherUser = userRepository.saveAndFlush(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        SubscriptionPlan plan = planRepository.saveAndFlush(
                new SubscriptionPlan(
                        uniquePlanCode(),
                        "Тестовый тариф"
                )
        );
        PlanPaymentOffer offer = offerRepository.saveAndFlush(
                new PlanPaymentOffer(
                        uniqueOfferCode(),
                        plan,
                        "TRIBUTE",
                        99900,
                        "RUB",
                        BillingPeriod.MONTH,
                        "product-" + UUID.randomUUID()
                )
        );
        Instant now = Instant.now();
        SubscriptionPurchaseIntent intent =
                SubscriptionPurchaseIntent.pending(
                        owner,
                        offer,
                        now,
                        now.plusSeconds(1800)
                );
        intent.attachCheckout("  checkout-123  ", now);
        repository.saveAndFlush(intent);
        UUID intentId = intent.getId();
        entityManager.clear();

        Optional<SubscriptionPurchaseIntent> byOwner =
                repository.findByIdAndUserIdForUpdate(
                        intentId,
                        owner.getId()
                );
        Optional<SubscriptionPurchaseIntent> byProvider =
                repository.findByIdAndProviderForUpdate(
                        intentId,
                        "TRIBUTE"
                );
        Optional<SubscriptionPurchaseIntent> byExternalCheckout =
                repository.findByProviderAndExternalCheckoutIdForUpdate(
                        "TRIBUTE",
                        "checkout-123"
                );

        assertAll(
                () -> assertTrue(byOwner.isPresent()),
                () -> assertTrue(byProvider.isPresent()),
                () -> assertTrue(byExternalCheckout.isPresent()),
                () -> assertEquals(
                        intentId,
                        byOwner.orElseThrow().getId()
                ),
                () -> assertEquals(
                        SubscriptionPurchaseIntentStatus.PENDING,
                        byOwner.orElseThrow().getStatus()
                ),
                () -> assertEquals(
                        "checkout-123",
                        byOwner.orElseThrow().getExternalCheckoutId()
                ),
                () -> assertEquals(
                        offer.getCode(),
                        byOwner.orElseThrow().getOffer().getCode()
                ),
                () -> assertEquals(
                        intentId,
                        byExternalCheckout.orElseThrow().getId()
                ),
                () -> assertNotNull(byOwner.orElseThrow().getCreatedAt()),
                () -> assertNotNull(byOwner.orElseThrow().getUpdatedAt()),
                () -> assertTrue(
                        repository.findByIdAndUserIdForUpdate(
                                intentId,
                                anotherUser.getId()
                        ).isEmpty()
                ),
                () -> assertTrue(
                        repository.findByIdAndProviderForUpdate(
                                intentId,
                                "OTHER_PROVIDER"
                        ).isEmpty()
                ),
                () -> assertTrue(
                        repository
                                .findByProviderAndExternalCheckoutIdForUpdate(
                                        "OTHER_PROVIDER",
                                        "checkout-123"
                                )
                                .isEmpty()
                ),
                () -> assertTrue(
                        repository
                                .findByProviderAndExternalCheckoutIdForUpdate(
                                        "TRIBUTE",
                                        "other-checkout"
                                )
                                .isEmpty()
                )
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
}
