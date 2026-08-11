package com.translatelab.backend.messaging.outbox.service;

import com.translatelab.backend.messaging.outbox.dto.ClaimedTranslationTask;
import com.translatelab.backend.messaging.publisher.TranslationTaskPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionException;

@Service
public class TranslationOutboxPublisherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TranslationOutboxPublisherService.class
    );

    private final TranslationOutboxTransactionService transactionService;
    private final TranslationTaskPublisher publisher;
    private final Counter publishedCounter;
    private final Counter retryCounter;
    private final Counter exhaustedCounter;
    private final Counter completionFailureCounter;

    public TranslationOutboxPublisherService(
            TranslationOutboxTransactionService transactionService,
            TranslationTaskPublisher publisher,
            MeterRegistry meterRegistry
    ) {
        this.transactionService = transactionService;
        this.publisher = publisher;
        this.publishedCounter = meterRegistry.counter(
                "translatelab.messaging.outbox.published"
        );
        this.retryCounter = meterRegistry.counter(
                "translatelab.messaging.outbox.retries"
        );
        this.exhaustedCounter = meterRegistry.counter(
                "translatelab.messaging.outbox.exhausted"
        );
        this.completionFailureCounter = meterRegistry.counter(
                "translatelab.messaging.outbox.completion.failures"
        );
    }

    public int publishBatch() {
        int submitted = 0;
        for (ClaimedTranslationTask task : transactionService.claimBatch()) {
            try {
                publisher.publishAsync(task.message()).whenComplete(
                        (ignored, failure) -> complete(task, failure)
                );
                submitted++;
            } catch (RuntimeException failure) {
                complete(task, failure);
            }
        }
        return submitted;
    }

    private void complete(
            ClaimedTranslationTask task,
            Throwable failure
    ) {
        try {
            if (failure == null) {
                if (transactionService.markPublished(
                        task.eventId(),
                        task.attempt()
                )) {
                    publishedCounter.increment();
                }
                return;
            }

            RuntimeException publishingFailure = toRuntimeException(failure);
            boolean exhausted = transactionService.recordFailure(
                    task.eventId(),
                    task.attempt(),
                    publishingFailure
            );
            if (exhausted) {
                exhaustedCounter.increment();
                LOGGER.warn(
                        "Outbox-событие {} исчерпало попытки публикации",
                        task.eventId()
                );
            } else {
                retryCounter.increment();
            }
        } catch (RuntimeException completionFailure) {
            completionFailureCounter.increment();
            LOGGER.error(
                    "Не удалось зафиксировать результат публикации outbox {}",
                    task.eventId(),
                    completionFailure
            );
        }
    }

    private RuntimeException toRuntimeException(Throwable failure) {
        Throwable cause = failure instanceof CompletionException
                && failure.getCause() != null
                ? failure.getCause()
                : failure;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(
                "Асинхронная публикация завершилась с ошибкой",
                cause
        );
    }
}
