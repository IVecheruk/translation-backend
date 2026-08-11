package com.translatelab.backend.plan;

import com.translatelab.backend.plan.dto.ResolvedEntitlement;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.plan.entity.PeriodType;
import com.translatelab.backend.plan.entity.PlanEntitlement;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.PlanEntitlementRepository;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.plan.service.EntitlementService;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class EntitlementServiceIntegrationTest {

    @Autowired
    private EntitlementService entitlementService;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private PlanEntitlementRepository planEntitlementRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @Test
    void shouldResolveSeededFreeTranslationEntitlement() {
        ResolvedEntitlement entitlement =
                entitlementService.resolveEntitlement(
                        UUID.randomUUID(),
                        FeatureCode.DOCUMENT_TRANSLATION
                );

        assertAll(
                () -> assertEquals("FREE", entitlement.planCode()),
                () -> assertEquals(
                        "Бесплатный",
                        entitlement.planDisplayName()
                ),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        entitlement.featureCode()
                ),
                () -> assertEquals(5, entitlement.limitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        entitlement.periodType()
                ),
                () -> assertFalse(entitlement.unlimited())
        );
    }

    @Test
    void shouldResolvePaidPlanFromEffectiveSubscription() {
        Instant now = clock.instant();
        String planCode = uniquePlanCode();
        User user = userRepository.save(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
        SubscriptionPlan plan = subscriptionPlanRepository.save(
                new SubscriptionPlan(
                        planCode,
                        "Профессиональный"
                )
        );
        planEntitlementRepository.save(
                PlanEntitlement.unlimited(
                        plan,
                        FeatureCode.DOCUMENT_TRANSLATION,
                        PeriodType.MONTH
                )
        );
        userSubscriptionRepository.saveAndFlush(
                UserSubscription.manual(
                        user,
                        plan,
                        now.minusSeconds(60),
                        now.plusSeconds(60)
                )
        );

        ResolvedEntitlement entitlement =
                entitlementService.resolveEntitlement(
                        user.getId(),
                        FeatureCode.DOCUMENT_TRANSLATION
                );

        assertAll(
                () -> assertEquals(
                        planCode,
                        entitlement.planCode()
                ),
                () -> assertEquals(
                        "Профессиональный",
                        entitlement.planDisplayName()
                ),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        entitlement.featureCode()
                ),
                () -> assertNull(entitlement.limitUnits()),
                () -> assertEquals(
                        PeriodType.MONTH,
                        entitlement.periodType()
                ),
                () -> assertTrue(entitlement.unlimited())
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
