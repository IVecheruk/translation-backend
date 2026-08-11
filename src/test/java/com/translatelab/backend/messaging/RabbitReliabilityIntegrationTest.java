package com.translatelab.backend.messaging;

import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.exception.MessagePublishingException;
import com.translatelab.backend.messaging.publisher.TranslationTaskPublisher;
import com.translatelab.backend.translation.entity.FileFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RabbitReliabilityIntegrationTest {

    private static final int LOAD_MESSAGE_COUNT = 100;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private MessagingProperties properties;
    @Autowired
    private TranslationTaskPublisher publisher;

    @BeforeEach
    @AfterEach
    void purgeTestQueues() {
        rabbitAdmin.purgeQueue(properties.queue(), true);
        rabbitAdmin.purgeQueue(properties.taskDeadLetterQueue(), true);
        rabbitAdmin.purgeQueue(properties.statusDeadLetterQueue(), true);
    }

    @Test
    void shouldReceiveAckAndPublishPersistentMessage() {
        TranslationTaskMessage task = task();

        publisher.publish(task);

        Message received = rabbitTemplate.receive(properties.queue(), 5_000);
        assertNotNull(received);
        assertEquals(
                MessageDeliveryMode.PERSISTENT,
                received.getMessageProperties().getReceivedDeliveryMode()
        );
        assertTrue(new String(
                received.getBody(),
                StandardCharsets.UTF_8
        ).contains(task.eventId().toString()));
    }

    @Test
    void shouldFailForMissingExchange() {
        TranslationTaskPublisher missingExchangePublisher =
                new TranslationTaskPublisher(
                        rabbitTemplate,
                        copyWith(
                                "missing." + UUID.randomUUID(),
                                properties.routingKey()
                        )
                );

        assertThrows(
                MessagePublishingException.class,
                () -> missingExchangePublisher.publish(task())
        );
    }

    @Test
    void shouldFailForUnroutableMandatoryMessage() {
        TranslationTaskPublisher unroutablePublisher =
                new TranslationTaskPublisher(
                        rabbitTemplate,
                        copyWith(
                                properties.exchange(),
                                "missing." + UUID.randomUUID()
                        )
                );

        assertThrows(
                MessagePublishingException.class,
                () -> unroutablePublisher.publish(task())
        );
    }

    @Test
    void shouldQuarantineMalformedStatusMessage() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbitTemplate.send(
                properties.exchange(),
                properties.statusRoutingKey(),
                new Message("{broken-json".getBytes(
                        StandardCharsets.UTF_8
                ), messageProperties)
        );

        Message deadLetter = rabbitTemplate.receive(
                properties.statusDeadLetterQueue(),
                5_000
        );

        assertNotNull(deadLetter);
        assertNotNull(deadLetter.getMessageProperties().getHeaders()
                .get("x-death"));
    }

    @Test
    void shouldPublishLoadWithoutMessageLoss() {
        for (int index = 0; index < LOAD_MESSAGE_COUNT; index++) {
            publisher.publish(task());
        }

        int received = 0;
        while (rabbitTemplate.receive(properties.queue(), 250) != null) {
            received++;
        }

        assertEquals(LOAD_MESSAGE_COUNT, received);
    }

    private TranslationTaskMessage task() {
        return new TranslationTaskMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "uploads/user/source.docx",
                "results/user/result.docx",
                "en",
                "ru",
                FileFormat.DOCX
        );
    }

    private MessagingProperties copyWith(
            String exchange,
            String routingKey
    ) {
        return new MessagingProperties(
                exchange,
                properties.queue(),
                routingKey,
                properties.statusQueue(),
                properties.statusRoutingKey(),
                properties.deadLetterExchange(),
                properties.taskDeadLetterQueue(),
                properties.taskDeadLetterRoutingKey(),
                properties.statusDeadLetterQueue(),
                properties.statusDeadLetterRoutingKey(),
                properties.confirmTimeout(),
                properties.taskQueueMaxLength(),
                properties.statusQueueMaxLength(),
                properties.maxMessageSize(),
                properties.listenerPrefetch(),
                properties.listenerConcurrency(),
                properties.listenerMaxConcurrency(),
                properties.statusRetryMaxAttempts(),
                properties.statusRetryInitialInterval(),
                properties.statusRetryMaxInterval(),
                properties.outboxPublishInterval(),
                properties.outboxBatchSize(),
                properties.outboxMaxAttempts(),
                properties.outboxInitialBackoff(),
                properties.outboxMaxBackoff(),
                properties.outboxClaimTimeout()
        );
    }
}
