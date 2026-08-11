package com.translatelab.backend.messaging.outbox.service;

import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.outbox.dto.ClaimedTranslationTask;
import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxEvent;
import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxStatus;
import com.translatelab.backend.messaging.outbox.repository.TranslationOutboxEventRepository;
import com.translatelab.backend.translation.entity.TranslationJob;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TranslationOutboxTransactionService {

    private final TranslationOutboxEventRepository repository;
    private final MessagingProperties properties;
    private final Clock clock;

    public TranslationOutboxTransactionService(
            TranslationOutboxEventRepository repository,
            MessagingProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<ClaimedTranslationTask> claimBatch() {
        Instant now = clock.instant();
        Instant lockedUntil = now.plus(properties.outboxClaimTimeout());
        List<TranslationOutboxEvent> events =
                repository.findClaimableForUpdate(
                        now,
                        PageRequest.of(0, properties.outboxBatchSize())
                );

        return events.stream().map(event -> {
            event.claim(lockedUntil);
            TranslationJob job = event.getJob();
            TranslationTaskMessage message = new TranslationTaskMessage(
                    event.getId(),
                    job.getId(),
                    job.getSourceFileKey(),
                    job.getExpectedResultFileKey(),
                    job.getSourceLang(),
                    job.getTargetLang(),
                    job.getFileFormat()
            );
            return new ClaimedTranslationTask(
                    event.getId(),
                    event.getAttemptCount(),
                    message
            );
        }).toList();
    }

    @Transactional
    public boolean markPublished(UUID eventId, int attempt) {
        TranslationOutboxEvent event = repository
                .findByIdForUpdate(eventId)
                .orElseThrow();
        if (event.getAttemptCount() != attempt
                || event.getStatus()
                != TranslationOutboxStatus.PUBLISHING) {
            return false;
        }
        event.markPublished(clock.instant());
        return true;
    }

    @Transactional
    public boolean recordFailure(
            UUID eventId,
            int attempt,
            RuntimeException failure
    ) {
        TranslationOutboxEvent event = repository
                .findByIdForUpdate(eventId)
                .orElseThrow();

        if (event.getAttemptCount() != attempt
                || event.getStatus()
                != TranslationOutboxStatus.PUBLISHING) {
            return false;
        }

        String diagnostic = failure.getClass().getSimpleName()
                + ": " + String.valueOf(failure.getMessage());
        if (attempt >= properties.outboxMaxAttempts()) {
            event.markExhausted(diagnostic);
            return true;
        }

        event.scheduleRetry(
                clock.instant().plus(calculateBackoff(attempt)),
                diagnostic
        );
        return false;
    }

    private Duration calculateBackoff(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration initial = properties.outboxInitialBackoff();
        Duration maximum = properties.outboxMaxBackoff();

        try {
            Duration calculated = initial.multipliedBy(multiplier);
            return calculated.compareTo(maximum) > 0
                    ? maximum
                    : calculated;
        } catch (ArithmeticException exception) {
            return maximum;
        }
    }
}
