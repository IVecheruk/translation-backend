package com.translatelab.backend.messaging.outbox.repository;

import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxEvent;
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

public interface TranslationOutboxEventRepository
        extends JpaRepository<TranslationOutboxEvent, UUID> {

    @Query(
            value = """
                    SELECT event.*
                    FROM translation_outbox_events event
                    WHERE (
                        event.status = 'PENDING'
                        AND event.available_at <= :now
                    ) OR (
                        event.status = 'PUBLISHING'
                        AND event.locked_until <= :now
                    )
                    ORDER BY event.available_at, event.id
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<TranslationOutboxEvent> findClaimableForUpdate(
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from TranslationOutboxEvent event where event.id = :id")
    Optional<TranslationOutboxEvent> findByIdForUpdate(@Param("id") UUID id);

    Optional<TranslationOutboxEvent> findByJob_Id(UUID jobId);

    long countByStatus(
            com.translatelab.backend.messaging.outbox.entity.TranslationOutboxStatus status
    );
}
