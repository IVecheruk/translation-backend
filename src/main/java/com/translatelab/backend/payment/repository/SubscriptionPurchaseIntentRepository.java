package com.translatelab.backend.payment.repository;

import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface SubscriptionPurchaseIntentRepository
        extends JpaRepository<SubscriptionPurchaseIntent, UUID> {

    Optional<SubscriptionPurchaseIntent> findByProviderAndExternalCheckoutId(
            String provider,
            String externalCheckoutId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT intent
              FROM SubscriptionPurchaseIntent intent
              WHERE intent.id = :intentId
                AND intent.user.id = :userId
              """)
    Optional<SubscriptionPurchaseIntent> findByIdAndUserIdForUpdate(
            @Param("intentId") UUID intentId,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT intent
              FROM SubscriptionPurchaseIntent intent
              WHERE intent.id = :intentId
                AND intent.provider = :provider
              """)
    Optional<SubscriptionPurchaseIntent> findByIdAndProviderForUpdate(
            @Param("intentId") UUID intentId,
            @Param("provider") String provider
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT intent
              FROM SubscriptionPurchaseIntent intent
              WHERE intent.provider = :provider
                AND intent.externalCheckoutId = :externalCheckoutId
              """)
    Optional<SubscriptionPurchaseIntent>
            findByProviderAndExternalCheckoutIdForUpdate(
                    @Param("provider") String provider,
                    @Param("externalCheckoutId") String externalCheckoutId
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT intent
              FROM SubscriptionPurchaseIntent intent
              WHERE intent.user.id = :userId
                AND intent.status =
                    com.translatelab.backend.payment.entity.SubscriptionPurchaseIntentStatus.PENDING
              """)
    Optional<SubscriptionPurchaseIntent> findPendingByUserIdForUpdate(
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              SELECT intent
              FROM SubscriptionPurchaseIntent intent
              WHERE intent.status =
                    com.translatelab.backend.payment.entity.SubscriptionPurchaseIntentStatus.PENDING
                AND intent.expiresAt <= :now
              ORDER BY intent.expiresAt, intent.id
              """)
    List<SubscriptionPurchaseIntent> findExpiredPendingForUpdate(
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            DELETE FROM subscription_purchase_intents
            WHERE id IN (
                SELECT id
                FROM subscription_purchase_intents
                WHERE status IN ('CONSUMED', 'EXPIRED', 'CANCELED')
                  AND updated_at < :cutoff
                ORDER BY updated_at, id
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteTerminalBefore(
            @Param("cutoff") Instant cutoff,
            @Param("batchSize") int batchSize
    );
}
