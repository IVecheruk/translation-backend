package com.translatelab.backend.messaging.outbox.entity;

import com.translatelab.backend.translation.entity.TranslationJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TranslationOutboxEventTest {

    private static final Instant NOW =
            Instant.parse("2026-08-08T10:00:00Z");

    @Test
    void shouldMoveThroughClaimRetryAndPublishLifecycle() {
        TranslationOutboxEvent event = new TranslationOutboxEvent(
                mock(TranslationJob.class),
                NOW
        );

        event.claim(NOW.plusSeconds(30));
        assertEquals(TranslationOutboxStatus.PUBLISHING, event.getStatus());
        assertEquals(1, event.getAttemptCount());

        event.scheduleRetry(NOW.plusSeconds(1), " temporary failure ");
        assertEquals(TranslationOutboxStatus.PENDING, event.getStatus());
        assertEquals("temporary failure", event.getLastError());
        assertNull(event.getLockedUntil());

        event.claim(NOW.plusSeconds(31));
        event.markPublished(NOW.plusSeconds(2));
        assertEquals(TranslationOutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(2, event.getAttemptCount());
        assertEquals(NOW.plusSeconds(2), event.getPublishedAt());
    }

    @Test
    void shouldTruncateFailureAndRejectInvalidTransition() {
        TranslationOutboxEvent event = new TranslationOutboxEvent(
                mock(TranslationJob.class),
                NOW
        );
        event.claim(NOW.plusSeconds(30));
        event.markExhausted("x".repeat(2_000));

        assertEquals(TranslationOutboxStatus.EXHAUSTED, event.getStatus());
        assertEquals(
                TranslationOutboxEvent.MAX_ERROR_LENGTH,
                event.getLastError().length()
        );
        assertThrows(
                IllegalStateException.class,
                () -> event.markPublished(NOW)
        );
    }
}
