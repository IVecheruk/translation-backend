package com.translatelab.backend.messaging.outbox.service;

import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.outbox.dto.ClaimedTranslationTask;
import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxEvent;
import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxStatus;
import com.translatelab.backend.messaging.outbox.repository.TranslationOutboxEventRepository;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.translatelab.backend.support.MessagingPropertiesTestFixture.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TranslationOutboxTransactionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-08T10:00:00Z");

    @Mock
    private TranslationOutboxEventRepository repository;
    @Mock
    private TranslationJob job;

    private TranslationOutboxTransactionService service;
    private TranslationOutboxEvent event;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        MessagingProperties properties = create();
        service = new TranslationOutboxTransactionService(
                repository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        eventId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        event = new TranslationOutboxEvent(job, NOW);
        ReflectionTestUtils.setField(event, "id", eventId);
        lenient().when(job.getId()).thenReturn(jobId);
        lenient().when(job.getSourceFileKey()).thenReturn("source.docx");
        lenient().when(job.getExpectedResultFileKey())
                .thenReturn("result.docx");
        lenient().when(job.getSourceLang()).thenReturn("en");
        lenient().when(job.getTargetLang()).thenReturn("ru");
        lenient().when(job.getFileFormat()).thenReturn(FileFormat.DOCX);
    }

    @Test
    void shouldReuseStableEventIdWhenExpiredClaimIsRecovered() {
        given(repository.findClaimableForUpdate(
                any(Instant.class),
                any(Pageable.class)
        )).willReturn(List.of(event));

        ClaimedTranslationTask first = service.claimBatch().getFirst();
        ClaimedTranslationTask recovered = service.claimBatch().getFirst();

        assertEquals(eventId, first.eventId());
        assertEquals(eventId, first.message().eventId());
        assertEquals(eventId, recovered.message().eventId());
        assertEquals(1, first.attempt());
        assertEquals(2, recovered.attempt());
    }

    @Test
    void shouldScheduleBoundedBackoffAfterFailure() {
        event.claim(NOW.plusSeconds(30));
        given(repository.findByIdForUpdate(eventId))
                .willReturn(Optional.of(event));

        boolean exhausted = service.recordFailure(
                eventId,
                1,
                new RuntimeException("unavailable")
        );

        assertFalse(exhausted);
        assertEquals(TranslationOutboxStatus.PENDING, event.getStatus());
        assertEquals(NOW.plusSeconds(1), event.getAvailableAt());
    }

    @Test
    void shouldIgnoreLateConfirmationFromOlderAttempt() {
        event.claim(NOW.plusSeconds(30));
        event.scheduleRetry(NOW, "first failure");
        event.claim(NOW.plusSeconds(30));
        given(repository.findByIdForUpdate(eventId))
                .willReturn(Optional.of(event));

        assertFalse(service.markPublished(eventId, 1));
        assertEquals(TranslationOutboxStatus.PUBLISHING, event.getStatus());
        assertTrue(service.markPublished(eventId, 2));
        assertEquals(TranslationOutboxStatus.PUBLISHED, event.getStatus());
    }
}
