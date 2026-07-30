package com.translatelab.backend.usage.repository;

import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.usage.entity.FeatureUsageRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureUsageRecordRepository extends JpaRepository<FeatureUsageRecord, UUID> {

    @Query("""
              SELECT COALESCE(SUM(usage.units), 0)
              FROM FeatureUsageRecord usage
              WHERE usage.user.id = :userId
                AND usage.featureCode = :featureCode
                AND usage.periodStart = :periodStart
                AND usage.periodEnd = :periodEnd
                AND usage.status IN (
                    com.translatelab.backend.usage.entity.UsageStatus.RESERVED,
                    com.translatelab.backend.usage.entity.UsageStatus.CONSUMED
                )
              """)
    long sumOccupiedUnits(
            @Param("userId") UUID userId,
            @Param("featureCode") FeatureCode featureCode,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT usage
            FROM FeatureUsageRecord usage
            WHERE usage.id = :reservationId
            """)
    Optional<FeatureUsageRecord> findByIdForUpdate(
            @Param("reservationId") UUID reservationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
          SELECT usage
          FROM FeatureUsageRecord usage
          WHERE usage.status =
              com.translatelab.backend.usage.entity.UsageStatus.RESERVED
            AND usage.expiresAt <= :now
          ORDER BY usage.expiresAt ASC, usage.id ASC
          """)
    List<FeatureUsageRecord> findExpiredReservationsForUpdate(
            @Param("now") Instant now,
            Pageable pageable
    );
}