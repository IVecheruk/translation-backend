package com.translatelab.backend.messaging.publisher;

import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.exception.MessagePublishingException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Component
public class TranslationTaskPublisher {

    private static final String PUBLIC_ERROR =
            "Не удалось подтвердить отправку задачи перевода в RabbitMQ";

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    public TranslationTaskPublisher(
            RabbitTemplate rabbitTemplate,
            MessagingProperties messagingProperties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messagingProperties = messagingProperties;
        this.rabbitTemplate.setMandatory(true);
    }

    public void publish(TranslationTaskMessage message) {
        try {
            publishAsync(message).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new MessagePublishingException(
                    PUBLIC_ERROR,
                    exception.getCause()
            );
        }
    }

    public CompletableFuture<Void> publishAsync(
            TranslationTaskMessage message
    ) {
        Objects.requireNonNull(
                message,
                "Сообщение задачи перевода не должно быть null"
        );
        Objects.requireNonNull(
                message.eventId(),
                "Идентификатор события не должен быть null"
        );

        CorrelationData correlationData = new CorrelationData(
                message.eventId().toString()
        );

        try {
            rabbitTemplate.convertAndSend(
                    messagingProperties.exchange(),
                    messagingProperties.routingKey(),
                    message,
                    this::prepareMessage,
                    correlationData
            );

        } catch (AmqpException exception) {
            return CompletableFuture.failedFuture(
                    new MessagePublishingException(PUBLIC_ERROR, exception)
            );
        }

        return correlationData.getFuture()
                .orTimeout(
                        messagingProperties.confirmTimeout().toMillis(),
                        TimeUnit.MILLISECONDS
                )
                .thenApply(confirm -> {
                    validateConfirm(correlationData, confirm);
                    return (Void) null;
                })
                .exceptionallyCompose(exception ->
                        CompletableFuture.<Void>failedFuture(
                                normalizeFailure(exception)
                        )
                );
    }

    private void validateConfirm(
            CorrelationData correlationData,
            CorrelationData.Confirm confirm
    ) {
        if (!confirm.ack()) {
            throw failure("RabbitMQ вернул NACK: " + confirm.reason());
        }
        if (correlationData.getReturned() != null) {
            throw failure("Сообщение не было маршрутизировано в очередь");
        }
    }

    private RuntimeException normalizeFailure(Throwable failure) {
        Throwable cause = failure instanceof CompletionException
                && failure.getCause() != null
                ? failure.getCause()
                : failure;
        if (cause instanceof MessagePublishingException exception) {
            return exception;
        }
        return new MessagePublishingException(PUBLIC_ERROR, cause);
    }

    private Message prepareMessage(Message message) {
        if (message.getBody().length
                > messagingProperties.maxMessageSize().toBytes()) {
            throw failure("Сообщение превышает допустимый размер");
        }

        message.getMessageProperties().setDeliveryMode(
                MessageDeliveryMode.PERSISTENT
        );
        return message;
    }

    private MessagePublishingException failure(String diagnostic) {
        return new MessagePublishingException(
                PUBLIC_ERROR,
                new IllegalStateException(diagnostic)
        );
    }
}
