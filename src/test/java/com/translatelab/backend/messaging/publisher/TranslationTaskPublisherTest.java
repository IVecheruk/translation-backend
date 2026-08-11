package com.translatelab.backend.messaging.publisher;

import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.exception.MessagePublishingException;
import com.translatelab.backend.translation.entity.FileFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.UUID;

import static com.translatelab.backend.support.MessagingPropertiesTestFixture.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TranslationTaskPublisherTest {

    private static final String EXCHANGE = "translation.exchange";
    private static final String ROUTING_KEY = "translation.task";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TranslationTaskPublisher publisher;
    private TranslationTaskMessage message;

    @BeforeEach
    void setUp() {
        publisher = new TranslationTaskPublisher(rabbitTemplate, create());
        clearInvocations(rabbitTemplate);
        message = message();
    }

    @Test
    void shouldPublishPersistentMessageAfterBrokerAck() {
        doAnswer(invocation -> {
            MessagePostProcessor processor = invocation.getArgument(3);
            Message amqpMessage = new Message(new byte[]{1, 2, 3});
            processor.postProcessMessage(amqpMessage);
            assertEquals(
                    MessageDeliveryMode.PERSISTENT,
                    amqpMessage.getMessageProperties().getDeliveryMode()
            );
            assertEquals(
                    message.eventId().toString(),
                    amqpMessage.getMessageProperties().getCorrelationId()
            );
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    @Test
    void shouldFailOnBrokerNack() {
        completeConfirm(false, "exchange is unavailable");

        MessagePublishingException exception = assertThrows(
                MessagePublishingException.class,
                () -> publisher.publish(message)
        );

        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void shouldFailWhenMessageIsReturnedAsUnroutable() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(new byte[]{1}),
                    312,
                    "NO_ROUTE",
                    EXCHANGE,
                    ROUTING_KEY
            ));
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        assertThrows(
                MessagePublishingException.class,
                () -> publisher.publish(message)
        );
    }

    @Test
    void shouldFailWhenConfirmTimesOut() {
        publisher = new TranslationTaskPublisher(
                rabbitTemplate,
                create(Duration.ofMillis(1), DataSize.ofKilobytes(64))
        );

        assertThrows(
                MessagePublishingException.class,
                () -> publisher.publish(message)
        );
    }

    @Test
    void shouldRejectNullMessageBeforePublishing() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> publisher.publish(null)
        );

        assertEquals(
                "Сообщение задачи перевода не должно быть null",
                exception.getMessage()
        );
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldWrapImmediateAmqpFailure() {
        AmqpException cause = new AmqpException("RabbitMQ unavailable");
        doThrow(cause).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        MessagePublishingException exception = assertThrows(
                MessagePublishingException.class,
                () -> publisher.publish(message)
        );

        assertSame(cause, exception.getCause());
    }

    private void completeConfirm(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(ack, reason)
            );
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    private TranslationTaskMessage message() {
        return new TranslationTaskMessage(
                UUID.fromString(
                        "32013136-d69f-4a77-b83e-81a529ad3f7d"
                ),
                UUID.fromString(
                        "9c2ad070-a91c-4b4d-99e1-bec77130c49d"
                ),
                "uploads/user-id/file-id.docx",
                "results/user-id/result-id.docx",
                "en",
                "ru",
                FileFormat.DOCX
        );
    }
}
