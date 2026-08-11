package com.translatelab.backend.plan;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.plan.entity.PlanEntitlement;
import com.translatelab.backend.plan.entity.PlanEntitlementId;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.PlanEntitlementRepository;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PlanRepositoryIntegrationTest {

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private PlanEntitlementRepository planEntitlementRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldReadSeededFreePlanAndEntitlement() {
        SubscriptionPlan plan = subscriptionPlanRepository
                .findById("FREE")
                .orElseThrow();
        PlanEntitlement entitlement = planEntitlementRepository
                .findByIdAndPlan_ActiveTrue(new PlanEntitlementId(
                        "FREE",
                        FeatureCode.DOCUMENT_TRANSLATION
                ))
                .orElseThrow();

        assertAll(
                () -> assertEquals("FREE", plan.getCode()),
                () -> assertEquals("Бесплатный", plan.getDisplayName()),
                () -> assertTrue(plan.isActive()),
                () -> assertEquals(5, entitlement.getLimitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        entitlement.getPeriodType()
                ),
                () -> assertFalse(entitlement.isUnlimited()),
                () -> assertEquals(
                        "FREE",
                        entitlement.getPlan().getCode()
                ),
                () -> assertTrue(
                        entityManagerFactory
                                .getPersistenceUnitUtil()
                                .isLoaded(entitlement, "plan")
                )
        );
    }

    @Test
    void shouldReadSeededProPlanAndEntitlement() {
        SubscriptionPlan plan = subscriptionPlanRepository
                .findById("PRO")
                .orElseThrow();
        PlanEntitlement entitlement = planEntitlementRepository
                .findByIdAndPlan_ActiveTrue(new PlanEntitlementId(
                        "PRO",
                        FeatureCode.DOCUMENT_TRANSLATION
                ))
                .orElseThrow();

        assertAll(
                () -> assertEquals("PRO", plan.getCode()),
                () -> assertEquals(
                        "Профессиональный",
                        plan.getDisplayName()
                ),
                () -> assertTrue(plan.isActive()),
                () -> assertEquals(100, entitlement.getLimitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        entitlement.getPeriodType()
                ),
                () -> assertFalse(entitlement.isUnlimited()),
                () -> assertEquals(
                        "PRO",
                        entitlement.getPlan().getCode()
                ),
                () -> assertTrue(
                        entityManagerFactory
                                .getPersistenceUnitUtil()
                                .isLoaded(entitlement, "plan")
                )
        );
    }

    @Test
    void shouldPersistUnlimitedEntitlementWithCompositeId() {
        String planCode = uniquePlanCode();
        SubscriptionPlan plan = subscriptionPlanRepository.save(
                new SubscriptionPlan(planCode, "Тестовый тариф")
        );
        PlanEntitlementId entitlementId = new PlanEntitlementId(
                planCode,
                FeatureCode.DOCUMENT_TRANSLATION
        );

        planEntitlementRepository.saveAndFlush(
                PlanEntitlement.unlimited(
                        plan,
                        FeatureCode.DOCUMENT_TRANSLATION,
                        PeriodType.MONTH
                )
        );
        entityManager.clear();

        PlanEntitlement saved = planEntitlementRepository
                .findByIdAndPlan_ActiveTrue(entitlementId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(entitlementId, saved.getId()),
                () -> assertEquals(planCode, saved.getPlan().getCode()),
                () -> assertNull(saved.getLimitUnits()),
                () -> assertTrue(saved.isUnlimited()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        saved.getPeriodType()
                ),
                () -> assertNotNull(saved.getCreatedAt()),
                () -> assertNotNull(saved.getUpdatedAt())
        );
    }

    @Test
    void shouldHideEntitlementOfInactivePlan() {
        String planCode = uniquePlanCode();
        SubscriptionPlan plan = subscriptionPlanRepository.save(
                new SubscriptionPlan(planCode, "Отключённый тариф")
        );
        PlanEntitlementId entitlementId = new PlanEntitlementId(
                planCode,
                FeatureCode.DOCUMENT_TRANSLATION
        );
        planEntitlementRepository.save(
                PlanEntitlement.limited(
                        plan,
                        FeatureCode.DOCUMENT_TRANSLATION,
                        10,
                        PeriodType.MONTH
                )
        );
        plan.deactivate();
        planEntitlementRepository.flush();
        entityManager.clear();

        assertTrue(
                planEntitlementRepository
                        .findByIdAndPlan_ActiveTrue(entitlementId)
                        .isEmpty()
        );
    }

    private String uniquePlanCode() {
        return "TEST_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}
