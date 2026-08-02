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

@Repository
public interface SubscriptionPurchaseIntentRepository
        extends JpaRepository<SubscriptionPurchaseIntent, UUID> {

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
}