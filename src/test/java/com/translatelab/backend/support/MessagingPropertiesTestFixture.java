package com.translatelab.backend.support;

import com.translatelab.backend.config.MessagingProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

public final class MessagingPropertiesTestFixture {

    private MessagingPropertiesTestFixture() {
    }

    public static MessagingProperties create() {
        return create(
                Duration.ofSeconds(5),
                DataSize.ofKilobytes(64)
        );
    }

    public static MessagingProperties create(
            Duration confirmTimeout,
            DataSize maxMessageSize
    ) {
        return new MessagingProperties(
                "translation.exchange",
                "translation.tasks.v2",
                "translation.task",
                "translation.status.v2",
                "translation.status",
                "translation.dlx",
                "translation.tasks.v2.dlq",
                "translation.task.dead",
                "translation.status.v2.dlq",
                "translation.status.dead",
                confirmTimeout,
                10_000,
                50_000,
                maxMessageSize,
                10,
                1,
                4,
                3,
                Duration.ofMillis(500),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                50,
                10,
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30)
        );
    }
}
