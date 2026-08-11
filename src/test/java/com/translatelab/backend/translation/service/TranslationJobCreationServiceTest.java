package com.translatelab.backend.translation.service;

import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxEvent;
import com.translatelab.backend.messaging.outbox.repository.TranslationOutboxEventRepository;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.usage.service.UsageLimitService;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TranslationJobCreationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TranslationJobRepository jobRepository;
    @Mock
    private TranslationOutboxEventRepository outboxRepository;
    @Mock
    private UsageLimitService usageLimitService;

    @Test
    void shouldReserveCreateOutboxAndConsumeInOneWorkflow() {
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        User user = mock(User.class);
        TranslationJob savedJob = mock(TranslationJob.class);
        given(usageLimitService.reserve(
                userId,
                FeatureCode.DOCUMENT_TRANSLATION,
                1
        )).willReturn(reservationId);
        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));
        given(jobRepository.save(org.mockito.ArgumentMatchers.any()))
                .willReturn(savedJob);
        given(savedJob.getId()).willReturn(jobId);
        TranslationJobCreationService service =
                new TranslationJobCreationService(
                        userRepository,
                        jobRepository,
                        outboxRepository,
                        usageLimitService,
                        Clock.fixed(now, ZoneOffset.UTC)
                );

        TranslationJob result = service.create(
                userId,
                "source.docx",
                "result.docx",
                "en",
                "ru",
                FileFormat.DOCX
        );

        assertSame(savedJob, result);
        ArgumentCaptor<TranslationOutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(TranslationOutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertSame(savedJob, outboxCaptor.getValue().getJob());
        assertEquals(now, outboxCaptor.getValue().getAvailableAt());
        InOrder order = inOrder(
                usageLimitService,
                jobRepository,
                outboxRepository
        );
        order.verify(usageLimitService).reserve(
                userId,
                FeatureCode.DOCUMENT_TRANSLATION,
                1
        );
        order.verify(jobRepository).save(
                org.mockito.ArgumentMatchers.any(TranslationJob.class)
        );
        order.verify(outboxRepository).save(
                org.mockito.ArgumentMatchers.any(TranslationOutboxEvent.class)
        );
        order.verify(usageLimitService).consume(reservationId, jobId);
    }
}
