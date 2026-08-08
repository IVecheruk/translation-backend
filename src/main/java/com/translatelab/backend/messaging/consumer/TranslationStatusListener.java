package com.translatelab.backend.messaging.consumer;

import com.translatelab.backend.messaging.dto.TranslationStatusMessage;
import com.translatelab.backend.messaging.exception.InvalidTranslationStatusMessageException;
import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.service.TranslationStatusUpdateService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TranslationStatusListener {

    private final TranslationStatusUpdateService statusUpdateService;
    private final MessagingProperties messagingProperties;

    public TranslationStatusListener(
            TranslationStatusUpdateService statusUpdateService,
            MessagingProperties messagingProperties
    ) {
        this.statusUpdateService = statusUpdateService;
        this.messagingProperties = messagingProperties;
    }

    @RabbitListener(queues = "${app.messaging.status-queue}")
    public void receive(
            TranslationStatusMessage message,
            Message rawMessage
    ) {
        if (rawMessage.getBody().length
                > messagingProperties.maxMessageSize().toBytes()) {
            throw new AmqpRejectAndDontRequeueException(
                    "Сообщение статуса превышает допустимый размер"
            );
        }

        consume(message);
    }

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
