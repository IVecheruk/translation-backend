package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static com.translatelab.backend.support.MessagingPropertiesTestFixture.create;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();
    private final MessagingProperties properties = create();

    @Test
    void shouldDeclareDurableTaskTopologyWithCapacityAndDeadLettering() {
        DirectExchange exchange = config.translationExchange(properties);
        Queue queue = config.translationQueue(properties);
        Binding binding = config.translationBinding(
                queue,
                exchange,
                properties
        );

        assertAll(
                () -> assertTrue(exchange.isDurable()),
                () -> assertTrue(queue.isDurable()),
                () -> assertEquals(
                        properties.routingKey(),
                        binding.getRoutingKey()
                ),
                () -> assertEquals(
                        properties.deadLetterExchange(),
                        queue.getArguments().get("x-dead-letter-exchange")
                ),
                () -> assertEquals(
                        properties.taskDeadLetterRoutingKey(),
                        queue.getArguments().get("x-dead-letter-routing-key")
                ),
                () -> assertEquals(
                        (long) properties.taskQueueMaxLength(),
                        queue.getArguments().get("x-max-length")
                ),
                () -> assertEquals(
                        "reject-publish-dlx",
                        queue.getArguments().get("x-overflow")
                )
        );
    }

    @Test
    void shouldDeclareDurableStatusAndDeadLetterTopology() {
        DirectExchange exchange = config.translationExchange(properties);
        DirectExchange deadLetterExchange =
                config.translationDeadLetterExchange(properties);
        Queue statusQueue = config.translationStatusQueue(properties);
        Queue deadLetterQueue =
                config.translationStatusDeadLetterQueue(properties);
        Binding statusBinding = config.translationStatusBinding(
                statusQueue,
                exchange,
                properties
        );
        Binding deadLetterBinding =
                config.translationStatusDeadLetterBinding(
                        deadLetterQueue,
                        deadLetterExchange,
                        properties
                );

        assertAll(
                () -> assertTrue(statusQueue.isDurable()),
                () -> assertTrue(deadLetterExchange.isDurable()),
                () -> assertTrue(deadLetterQueue.isDurable()),
                () -> assertEquals(
                        properties.statusRoutingKey(),
                        statusBinding.getRoutingKey()
                ),
                () -> assertEquals(
                        properties.statusDeadLetterRoutingKey(),
                        deadLetterBinding.getRoutingKey()
                ),
                () -> assertEquals(
                        (long) properties.statusQueueMaxLength(),
                        statusQueue.getArguments().get("x-max-length")
                )
        );
    }
}
