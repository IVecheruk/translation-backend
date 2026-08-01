package com.translatelab.backend.subscription.repository;

import com.translatelab.backend.subscription.entity.UserSubscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    @EntityGraph(attributePaths = "plan")
    @Query("""
              SELECT subscription
              FROM UserSubscription subscription
              WHERE subscription.user.id = :userId
                AND subscription.status =
                    com.translatelab.backend.subscription.entity.SubscriptionStatus.ACTIVE
                AND subscription.plan.active = TRUE
                AND subscription.currentPeriodStart <= :now
                AND subscription.currentPeriodEnd > :now
              """)
    Optional<UserSubscription> findEffectiveActiveByUserIdAt(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );
}