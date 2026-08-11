package com.translatelab.backend.messaging.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static com.translatelab.backend.support.MessagingPropertiesTestFixture.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitQueueMetricsTest {

    @Test
    void shouldExposeBothDeadLetterQueueDepths() {
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        QueueInformation taskInfo = mock(QueueInformation.class);
        QueueInformation statusInfo = mock(QueueInformation.class);
        when(taskInfo.getMessageCount()).thenReturn(7L);
        when(statusInfo.getMessageCount()).thenReturn(3L);
        when(rabbitAdmin.getQueueInfo("translation.tasks.v2.dlq"))
                .thenReturn(taskInfo);
        when(rabbitAdmin.getQueueInfo("translation.status.v2.dlq"))
                .thenReturn(statusInfo);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RabbitQueueMetrics metrics = new RabbitQueueMetrics(
                rabbitAdmin,
                create(),
                registry
        );

        metrics.refresh();

        assertEquals(
                7.0,
                registry.get("translation.rabbit.dead.letters")
                        .tag("message_type", "task")
                        .gauge()
                        .value()
        );
        assertEquals(
                3.0,
                registry.get("translation.rabbit.dead.letters")
                        .tag("message_type", "status")
                        .gauge()
                        .value()
        );
    }
}
