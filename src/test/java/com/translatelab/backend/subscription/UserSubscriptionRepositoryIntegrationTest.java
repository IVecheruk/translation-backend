package com.translatelab.backend.subscription;

import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.subscription.entity.SubscriptionStatus;
import com.translatelab.backend.subscription.entity.UserSubscription;
import com.translatelab.backend.subscription.repository.UserSubscriptionRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class UserSubscriptionRepositoryIntegrationTest {

    private static final Instant PERIOD_START = Instant.parse(
            "2026-08-01T00:00:00Z"
    );

    private static final Instant PERIOD_END = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldPersistAndFindEffectiveProviderManagedSubscription() {
        User user = saveUser();
        SubscriptionPlan plan = savePlan();
        UserSubscription saved = userSubscriptionRepository.saveAndFlush(
                UserSubscription.providerManaged(
                        user,
                        plan,
                        PERIOD_START,
                        PERIOD_END,
                        "TRIBUTE",
                        "  customer-123  ",
                        "  subscription-456  "
                )
        );
        UUID subscriptionId = saved.getId();
        entityManager.clear();

        UserSubscription found = userSubscriptionRepository
                .findEffectiveActiveByUserIdAt(
                        user.getId(),
                        PERIOD_START.plusSeconds(1)
                )
                .orElseThrow();

        assertAll(
                () -> assertEquals(subscriptionId, found.getId()),
                () -> assertEquals(user.getId(), found.getUser().getId()),
                () -> assertEquals(plan.getCode(), found.getPlan().getCode()),
                () -> assertEquals(
                        SubscriptionStatus.ACTIVE,
                        found.getStatus()
                ),
                () -> assertEquals(
                        PERIOD_START,
                        found.getCurrentPeriodStart()
                ),
                () -> assertEquals(
                        PERIOD_END,
                        found.getCurrentPeriodEnd()
                ),
                () -> assertFalse(found.isCancelAtPeriodEnd()),
                () -> assertEquals("TRIBUTE", found.getProvider()),
                () -> assertEquals(
                        "customer-123",
                        found.getExternalCustomerId()
                ),
                () -> assertEquals(
                        "subscription-456",
                        found.getExternalSubscriptionId()
                ),
                () -> assertNotNull(found.getCreatedAt()),
                () -> assertNotNull(found.getUpdatedAt()),
                () -> assertTrue(
                        entityManagerFactory
                                .getPersistenceUnitUtil()
                                .isLoaded(found, "plan")
                )
        );
    }

    @Test
    void shouldUseInclusiveStartAndExclusiveEnd() {
        User user = saveUser();
        saveManualSubscription(user, savePlan());
        entityManager.clear();

        assertAll(
                () -> assertTrue(
                        userSubscriptionRepository
                                .findEffectiveActiveByUserIdAt(
                                        user.getId(),
                                        PERIOD_START.minusSeconds(1)
                                )
                                .isEmpty()
                ),
                () -> assertTrue(
                        userSubscriptionRepository
                                .findEffectiveActiveByUserIdAt(
                                        user.getId(),
                                        PERIOD_START
                                )
                                .isPresent()
                ),
                () -> assertTrue(
                        userSubscriptionRepository
                                .findEffectiveActiveByUserIdAt(
                                        user.getId(),
                                        PERIOD_END
                                )
                                .isEmpty()
                )
        );
    }

    @Test
    void shouldNotReturnAnotherUsersSubscription() {
        User owner = saveUser();
        User anotherUser = saveUser();
        saveManualSubscription(owner, savePlan());
        entityManager.clear();

        assertTrue(
                userSubscriptionRepository
                        .findEffectiveActiveByUserIdAt(
                                anotherUser.getId(),
                                PERIOD_START.plusSeconds(1)
                        )
                        .isEmpty()
        );
    }

    @Test
    void shouldIgnoreSubscriptionThatIsNotActive() {
        User user = saveUser();
        UserSubscription subscription = saveManualSubscription(
                user,
                savePlan()
        );

        entityManager.createNativeQuery("""
                        UPDATE user_subscriptions
                        SET status = 'PAST_DUE'
                        WHERE id = :subscriptionId
                        """)
                .setParameter("subscriptionId", subscription.getId())
                .executeUpdate();
        entityManager.clear();

        assertTrue(
                userSubscriptionRepository
                        .findEffectiveActiveByUserIdAt(
                                user.getId(),
                                PERIOD_START.plusSeconds(1)
                        )
                        .isEmpty()
        );
    }

    @Test
    void shouldIgnoreSubscriptionOfInactivePlan() {
        User user = saveUser();
        SubscriptionPlan plan = savePlan();
        saveManualSubscription(user, plan);

        plan.deactivate();
        subscriptionPlanRepository.flush();
        entityManager.clear();

        assertTrue(
                userSubscriptionRepository
                        .findEffectiveActiveByUserIdAt(
                                user.getId(),
                                PERIOD_START.plusSeconds(1)
                        )
                        .isEmpty()
        );
    }

    private UserSubscription saveManualSubscription(
            User user,
            SubscriptionPlan plan
    ) {
        return userSubscriptionRepository.saveAndFlush(
                UserSubscription.manual(
                        user,
                        plan,
                        PERIOD_START,
                        PERIOD_END
                )
        );
    }

    private User saveUser() {
        return userRepository.save(
                new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )
        );
    }

    private SubscriptionPlan savePlan() {
        return subscriptionPlanRepository.save(
                new SubscriptionPlan(
                        uniquePlanCode(),
                        "Тестовый тариф"
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
}
