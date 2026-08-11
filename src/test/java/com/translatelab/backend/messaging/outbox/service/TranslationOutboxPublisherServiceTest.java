package com.translatelab.backend.messaging.outbox.service;

import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.outbox.dto.ClaimedTranslationTask;
import com.translatelab.backend.messaging.publisher.TranslationTaskPublisher;
import com.translatelab.backend.translation.entity.FileFormat;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TranslationOutboxPublisherServiceTest {

    @Mock
    private TranslationOutboxTransactionService transactionService;
    @Mock
    private TranslationTaskPublisher publisher;

    @Test
    void shouldMarkEventOnlyAfterBrokerConfirmation() {
        ClaimedTranslationTask task = task();
        given(transactionService.claimBatch()).willReturn(List.of(task));
        given(publisher.publishAsync(task.message()))
                .willReturn(CompletableFuture.completedFuture(null));
        given(transactionService.markPublished(
                task.eventId(),
                task.attempt()
        )).willReturn(true);
        TranslationOutboxPublisherService service = service();

        int published = service.publishBatch();

        assertEquals(1, published);
        InOrder order = inOrder(publisher, transactionService);
        order.verify(publisher).publishAsync(task.message());
        order.verify(transactionService).markPublished(
                task.eventId(),
                task.attempt()
        );
        verify(transactionService, never()).recordFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void shouldScheduleRetryAfterPublishFailure() {
        ClaimedTranslationTask task = task();
        RuntimeException failure = new RuntimeException("NACK");
        given(transactionService.claimBatch()).willReturn(List.of(task));
        given(publisher.publishAsync(task.message()))
                .willReturn(CompletableFuture.failedFuture(failure));
        given(transactionService.recordFailure(
                task.eventId(),
                task.attempt(),
                failure
        )).willReturn(false);
        TranslationOutboxPublisherService service = service();

        assertEquals(1, service.publishBatch());

        verify(transactionService).recordFailure(
                task.eventId(),
                task.attempt(),
                failure
        );
        verify(transactionService, never()).markPublished(
                task.eventId(),
                task.attempt()
        );
    }

    @Test
    void shouldNotCountStaleConfirmationAsPublished() {
        ClaimedTranslationTask task = task();
        given(transactionService.claimBatch()).willReturn(List.of(task));
        given(publisher.publishAsync(task.message()))
                .willReturn(CompletableFuture.completedFuture(null));
        given(transactionService.markPublished(
                task.eventId(),
                task.attempt()
        )).willReturn(false);

        assertEquals(1, service().publishBatch());
    }

    @Test
    void shouldReturnWithoutWaitingForBrokerConfirm() {
        ClaimedTranslationTask task = task();
        CompletableFuture<Void> confirm = new CompletableFuture<>();
        given(transactionService.claimBatch()).willReturn(List.of(task));
        given(publisher.publishAsync(task.message())).willReturn(confirm);
        TranslationOutboxPublisherService service = service();

        assertEquals(1, service.publishBatch());
        verify(transactionService, never()).markPublished(
                task.eventId(),
                task.attempt()
        );

        confirm.complete(null);
        verify(transactionService).markPublished(
                task.eventId(),
                task.attempt()
        );
    }

    private TranslationOutboxPublisherService service() {
        return new TranslationOutboxPublisherService(
                transactionService,
                publisher,
                new SimpleMeterRegistry()
        );
    }

    private ClaimedTranslationTask task() {
        UUID eventId = UUID.randomUUID();
        return new ClaimedTranslationTask(
                eventId,
                2,
                new TranslationTaskMessage(
                        eventId,
                        UUID.randomUUID(),
                        "source.docx",
                        "result.docx",
                        "en",
                        "ru",
                        FileFormat.DOCX
                )
        );
    }
}
