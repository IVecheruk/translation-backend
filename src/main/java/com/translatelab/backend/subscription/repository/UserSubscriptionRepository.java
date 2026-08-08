package com.translatelab.backend.subscription.repository;

import com.translatelab.backend.subscription.entity.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Pageable;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT subscription
              FROM UserSubscription subscription
              WHERE subscription.provider = :provider
                AND subscription.externalSubscriptionId =
                    :externalSubscriptionId
              """)
    Optional<UserSubscription> findByProviderAndExternalSubscriptionIdForUpdate(
            @Param("provider") String provider,
            @Param("externalSubscriptionId") String externalSubscriptionId
    );

    @EntityGraph(attributePaths = "plan")
    @Query("""
              SELECT subscription
              FROM UserSubscription subscription
              WHERE subscription.user.id = :userId
                AND subscription.status IN (
                    com.translatelab.backend.subscription.entity.SubscriptionStatus.ACTIVE,
                    com.translatelab.backend.subscription.entity.SubscriptionStatus.PAST_DUE
                )
                AND subscription.currentPeriodStart <= :now
                AND subscription.currentPeriodEnd > :now
              """)
    Optional<UserSubscription> findCurrentByUserIdAt(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT subscription
              FROM UserSubscription subscription
              WHERE subscription.provider = :provider
                AND subscription.externalOrderId = :externalOrderId
              """)
    Optional<UserSubscription> findByProviderAndExternalOrderIdForUpdate(
            @Param("provider") String provider,
            @Param("externalOrderId") String externalOrderId
    );

    @EntityGraph(attributePaths = "plan")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT subscription
              FROM UserSubscription subscription
              WHERE subscription.user.id = :userId
                AND subscription.status IN (
                    com.translatelab.backend.subscription.entity.SubscriptionStatus.ACTIVE,
                    com.translatelab.backend.subscription.entity.SubscriptionStatus.PAST_DUE
                )
              """)
    Optional<UserSubscription> findLiveByUserIdForUpdate(
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT subscription
              FROM UserSubscription subscription
              WHERE subscription.status IN (
                    com.translatelab.backend.subscription.entity.SubscriptionStatus.ACTIVE,
                    com.translatelab.backend.subscription.entity.SubscriptionStatus.PAST_DUE
                )
                AND subscription.currentPeriodEnd <= :now
              ORDER BY subscription.currentPeriodEnd, subscription.id
              """)
    List<UserSubscription> findExpiredLiveForUpdate(
            @Param("now") Instant now,
            Pageable pageable
    );
}
