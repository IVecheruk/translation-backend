package com.translatelab.backend.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.retry.RetryPolicySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RabbitRetryConfigTest {

    @Test
    void shouldNotRetryPermanentMessageFailures() {
        RetryPolicySettings settings = new RetryPolicySettings();

        new RabbitRetryConfig()
                .permanentMessageFailureRetryCustomizer()
                .customize(settings);

        assertEquals(
                java.util.List.of(
                        AmqpRejectAndDontRequeueException.class,
                        MessageConversionException.class
                ),
                settings.getExceptionExcludes()
        );
    }

    @Test
    void shouldRejectExhaustedMessageAndIncrementMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageRecoverer recoverer = new RabbitRetryConfig()
                .messageRecoverer(registry);

        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> recoverer.recover(
                        new Message(new byte[]{1}),
                        new RuntimeException("database unavailable")
                )
        );

        assertEquals(
                1.0,
                registry.counter(
                        "translation.rabbit.status.retry.exhausted"
                ).count()
        );
    }
}
