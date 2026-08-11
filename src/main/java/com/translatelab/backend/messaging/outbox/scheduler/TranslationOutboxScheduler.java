package com.translatelab.backend.messaging.outbox.scheduler;

import com.translatelab.backend.messaging.outbox.service.TranslationOutboxPublisherService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TranslationOutboxScheduler {

    private final TranslationOutboxPublisherService publisherService;

    public TranslationOutboxScheduler(
            TranslationOutboxPublisherService publisherService
    ) {
        this.publisherService = publisherService;
    }

    @Scheduled(
            fixedDelayString = "${app.messaging.outbox-publish-interval}"
    )
    public void publishPendingEvents() {
        publisherService.publishBatch();
    }
}
