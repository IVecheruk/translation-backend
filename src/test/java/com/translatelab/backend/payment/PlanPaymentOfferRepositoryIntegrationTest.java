package com.translatelab.backend.payment;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PlanPaymentOfferRepositoryIntegrationTest {

    @Autowired
    private PlanPaymentOfferRepository planPaymentOfferRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldReadSeededProOfferInActiveCatalog() {
        PlanPaymentOffer offer = planPaymentOfferRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        "PRO",
                        "TRIBUTE",
                        BillingPeriod.MONTH
                )
                .orElseThrow();
        List<String> catalogCodes = planPaymentOfferRepository
                .findActiveCatalog("TRIBUTE", BillingPeriod.MONTH)
                .stream()
                .map(PlanPaymentOffer::getCode)
                .toList();

        assertAll(
                () -> assertEquals(
                        "PRO_TRIBUTE_MONTH",
                        offer.getCode()
                ),
                () -> assertEquals("PRO", offer.getPlan().getCode()),
                () -> assertEquals(
                        "Профессиональный",
                        offer.getPlan().getDisplayName()
                ),
                () -> assertEquals("TRIBUTE", offer.getProvider()),
                () -> assertEquals(49900L, offer.getPriceMinor()),
                () -> assertEquals("RUB", offer.getCurrency()),
                () -> assertEquals(
                        BillingPeriod.MONTH,
                        offer.getBillingPeriod()
                ),
                () -> assertNull(offer.getExternalProductId()),
                () -> assertTrue(offer.isActive()),
                () -> assertNotNull(offer.getCreatedAt()),
                () -> assertNotNull(offer.getUpdatedAt()),
                () -> assertTrue(catalogCodes.contains(
                        "PRO_TRIBUTE_MONTH"
                ))
        );
    }

    @Test
    void shouldPersistAndFindActiveOffer() {
        SubscriptionPlan plan = savedPlan();
        String offerCode = uniqueOfferCode();
        planPaymentOfferRepository.saveAndFlush(new PlanPaymentOffer(
                offerCode,
                plan,
                "TRIBUTE",
                99900,
                "RUB",
                BillingPeriod.MONTH,
                uniqueExternalProductId()
        ));
        entityManager.clear();

        PlanPaymentOffer found = planPaymentOfferRepository
                .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                        plan.getCode(),
                        "TRIBUTE",
                        BillingPeriod.MONTH
                )
                .orElseThrow();
        boolean planWasLoaded = entityManagerFactory
                .getPersistenceUnitUtil()
                .isLoaded(found, "plan");

        assertAll(
                () -> assertEquals(offerCode, found.getCode()),
                () -> assertFalse(planWasLoaded),
                () -> assertEquals(plan.getCode(), found.getPlan().getCode()),
                () -> assertEquals("TRIBUTE", found.getProvider()),
                () -> assertEquals(99900L, found.getPriceMinor()),
                () -> assertEquals("RUB", found.getCurrency()),
                () -> assertEquals(
                        BillingPeriod.MONTH,
                        found.getBillingPeriod()
                ),
                () -> assertTrue(found.isActive()),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt())
        );
    }

    @Test
    void shouldHideInactiveOffer() {
        SubscriptionPlan plan = savedPlan();
        PlanPaymentOffer offer = planPaymentOfferRepository.save(
                newOffer(plan)
        );
        offer.deactivate();
        planPaymentOfferRepository.flush();
        entityManager.clear();

        assertTrue(
                planPaymentOfferRepository
                        .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                                plan.getCode(),
                                "TRIBUTE",
                                BillingPeriod.MONTH
                        )
                        .isEmpty()
        );
    }

    @Test
    void shouldHideOfferOfInactivePlan() {
        SubscriptionPlan plan = savedPlan();
        planPaymentOfferRepository.save(newOffer(plan));
        plan.deactivate();
        planPaymentOfferRepository.flush();
        entityManager.clear();

        assertTrue(
                planPaymentOfferRepository
                        .findByPlan_CodeAndProviderAndBillingPeriodAndActiveTrueAndPlan_ActiveTrue(
                                plan.getCode(),
                                "TRIBUTE",
                                BillingPeriod.MONTH
                        )
                        .isEmpty()
        );
    }

    @Test
    void shouldLoadInactiveOfferByInheritedIdLookup() {
        SubscriptionPlan plan = savedPlan();
        PlanPaymentOffer offer = newOffer(plan);
        String offerCode = offer.getCode();
        offer.deactivate();
        planPaymentOfferRepository.saveAndFlush(offer);
        entityManager.clear();

        PlanPaymentOffer found = planPaymentOfferRepository
                .findById(offerCode)
                .orElseThrow();

        assertAll(
                () -> assertEquals(offerCode, found.getCode()),
                () -> assertFalse(found.isActive())
        );
    }

    @Test
    void shouldReturnFetchedActiveCatalogInDeterministicOrder() {
        String catalogProvider = "TEST_PROVIDER";
        SubscriptionPlan cheaperA = savedPlan(
                "A_",
                "Первый дешёвый тариф"
        );
        SubscriptionPlan cheaperB = savedPlan(
                "B_",
                "Второй дешёвый тариф"
        );
        SubscriptionPlan expensive = savedPlan(
                "C_",
                "Дорогой тариф"
        );
        SubscriptionPlan inactiveOfferPlan = savedPlan(
                "D_",
                "Тариф с выключенным предложением"
        );
        SubscriptionPlan inactivePlan = savedPlan(
                "E_",
                "Выключенный тариф"
        );
        SubscriptionPlan otherProviderPlan = savedPlan(
                "F_",
                "Тариф другого провайдера"
        );

        PlanPaymentOffer cheaperBOffer = savedOffer(
                cheaperB,
                catalogProvider,
                9900
        );
        PlanPaymentOffer expensiveOffer = savedOffer(
                expensive,
                catalogProvider,
                19900
        );
        PlanPaymentOffer cheaperAOffer = savedOffer(
                cheaperA,
                catalogProvider,
                9900
        );
        PlanPaymentOffer inactiveOffer = savedOffer(
                inactiveOfferPlan,
                catalogProvider,
                29900
        );
        savedOffer(
                inactivePlan,
                catalogProvider,
                39900
        );
        savedOffer(
                otherProviderPlan,
                "OTHER_PROVIDER",
                4900
        );
        inactiveOffer.deactivate();
        inactivePlan.deactivate();
        planPaymentOfferRepository.flush();
        entityManager.clear();

        List<PlanPaymentOffer> catalog = planPaymentOfferRepository
                .findActiveCatalog(
                        catalogProvider,
                        BillingPeriod.MONTH
                );

        assertAll(
                () -> assertEquals(
                        List.of(
                                cheaperAOffer.getCode(),
                                cheaperBOffer.getCode(),
                                expensiveOffer.getCode()
                        ),
                        catalog.stream()
                                .map(PlanPaymentOffer::getCode)
                                .toList()
                ),
                () -> assertEquals(
                        List.of(
                                cheaperA.getCode(),
                                cheaperB.getCode(),
                                expensive.getCode()
                        ),
                        catalog.stream()
                                .map(offer -> offer.getPlan().getCode())
                                .toList()
                ),
                () -> assertTrue(catalog.stream().allMatch(offer ->
                        entityManagerFactory
                                .getPersistenceUnitUtil()
                                .isLoaded(offer, "plan")
                ))
        );
    }

    private SubscriptionPlan savedPlan() {
        return savedPlan("PLAN_", "Тестовый тариф");
    }

    private SubscriptionPlan savedPlan(
            String codePrefix,
            String displayName
    ) {
        return subscriptionPlanRepository.save(
                new SubscriptionPlan(
                        uniqueCode(codePrefix),
                        displayName
                )
        );
    }

    private PlanPaymentOffer savedOffer(
            SubscriptionPlan plan,
            String provider,
            long priceMinor
    ) {
        return planPaymentOfferRepository.save(new PlanPaymentOffer(
                uniqueOfferCode(),
                plan,
                provider,
                priceMinor,
                "RUB",
                BillingPeriod.MONTH,
                uniqueExternalProductId()
        ));
    }

    private PlanPaymentOffer newOffer(SubscriptionPlan plan) {
        return new PlanPaymentOffer(
                uniqueOfferCode(),
                plan,
                "TRIBUTE",
                99900,
                "RUB",
                BillingPeriod.MONTH,
                uniqueExternalProductId()
        );
    }

    private String uniquePlanCode() {
        return uniqueCode("PLAN_");
    }

    private String uniqueOfferCode() {
        return uniqueCode("OFFER_");
    }

    private String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private String uniqueExternalProductId() {
        return "product-" + UUID.randomUUID();
    }
}
