package com.translatelab.backend.messaging.consumer;

import com.translatelab.backend.messaging.dto.TranslationStatusMessage;
import com.translatelab.backend.messaging.exception.InvalidTranslationStatusMessageException;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.service.TranslationStatusUpdateService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TranslationStatusListener {

    private final TranslationStatusUpdateService statusUpdateService;

    public TranslationStatusListener(
            TranslationStatusUpdateService statusUpdateService
    ) {
        this.statusUpdateService = statusUpdateService;
    }

    @RabbitListener(queues = "${app.messaging.status-queue}")
    public void consume(TranslationStatusMessage message) {
        try {
            statusUpdateService.updateStatus(message);
        } catch (
                InvalidTranslationStatusMessageException
                | TranslationJobNotFoundException exception
        ) {
            throw new AmqpRejectAndDontRequeueException(
                    "Некорректное сообщение статуса перевода",
                    exception
            );
        }
    }
}
