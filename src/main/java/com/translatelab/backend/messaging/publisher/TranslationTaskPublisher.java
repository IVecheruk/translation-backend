package com.translatelab.backend.messaging.publisher;

import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.exception.MessagePublishingException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class TranslationTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public TranslationTaskPublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
    }

    public void publish(TranslationTaskMessage message) {
        Objects.requireNonNull(
                message,
                "Сообщение задачи перевода не должно быть null"
        );

        try {
            rabbitTemplate.convertAndSend(
                    messagingProperties.exchange(),
                    messagingProperties.routingKey(),
                    message
            );
        } catch (AmqpException exception) {
            throw new MessagePublishingException(
                    "Не удалось отправить задачу перевода в RabbitMQ",
                    exception
            );
        }
    }
}