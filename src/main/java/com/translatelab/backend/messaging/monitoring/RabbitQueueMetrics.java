package com.translatelab.backend.messaging.monitoring;

import com.translatelab.backend.config.MessagingProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RabbitQueueMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RabbitQueueMetrics.class
    );

    private final RabbitAdmin rabbitAdmin;
    private final MessagingProperties properties;
    private final AtomicLong taskDeadLetters = new AtomicLong();
    private final AtomicLong statusDeadLetters = new AtomicLong();

    public RabbitQueueMetrics(
            RabbitAdmin rabbitAdmin,
            MessagingProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;

        registerGauge(
                meterRegistry,
                "task",
                properties.taskDeadLetterQueue(),
                taskDeadLetters
        );
        registerGauge(
                meterRegistry,
                "status",
                properties.statusDeadLetterQueue(),
                statusDeadLetters
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${app.messaging.observation-interval:30s}"
    )
    public void refresh() {
        update(properties.taskDeadLetterQueue(), taskDeadLetters);
        update(properties.statusDeadLetterQueue(), statusDeadLetters);
    }

    private void update(String queueName, AtomicLong target) {
        QueueInformation information = rabbitAdmin.getQueueInfo(queueName);
        long messageCount = information == null
                ? 0
                : information.getMessageCount();
        long previous = target.getAndSet(messageCount);
        if (messageCount > 0 && messageCount != previous) {
            LOGGER.warn(
                    "В RabbitMQ DLQ {} находится сообщений: {}",
                    queueName,
                    messageCount
            );
        } else if (messageCount == 0 && previous > 0) {
            LOGGER.info("RabbitMQ DLQ {} очищена", queueName);
        }
    }

    private void registerGauge(
            MeterRegistry registry,
            String messageType,
            String queueName,
            AtomicLong value
    ) {
        Gauge.builder(
                        "translation.rabbit.dead.letters",
                        value,
                        AtomicLong::get
                )
                .description("Количество сообщений в RabbitMQ DLQ")
                .tag("message_type", messageType)
                .tag("queue", queueName)
                .register(registry);
    }
}
