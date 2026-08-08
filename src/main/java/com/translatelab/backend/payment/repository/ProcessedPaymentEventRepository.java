package com.translatelab.backend.payment.repository;

import com.translatelab.backend.payment.entity.ProcessedPaymentEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.time.Instant;

public interface ProcessedPaymentEventRepository
        extends Repository<ProcessedPaymentEvent, UUID> {

    @Modifying
    @Query(
            value = """
                      INSERT INTO processed_payment_events (
                          id,
                          provider,
                          external_event_id,
                          event_type
                      )
                      VALUES (
                          :id,
                          :provider,
                          :externalEventId,
                          :eventType
                      )
                      ON CONFLICT (provider, external_event_id) DO NOTHING
                      """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("provider") String provider,
            @Param("externalEventId") String externalEventId,
            @Param("eventType") String eventType
    );

    @Modifying
    @Query(value = """
            DELETE FROM processed_payment_events
            WHERE id IN (
                SELECT id
                FROM processed_payment_events
                WHERE processed_at < :cutoff
                ORDER BY processed_at, id
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteProcessedBefore(
            @Param("cutoff") Instant cutoff,
            @Param("batchSize") int batchSize
    );
}
