package com.translatelab.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.messaging")
@Validated
public record MessagingProperties(

        @NotBlank
        String exchange,

        @NotBlank
        String queue,

        @NotBlank
        String routingKey,

        @NotBlank
        String statusQueue,

        @NotBlank
        String statusRoutingKey,

        @NotBlank
        String deadLetterExchange,

        @NotBlank
        String taskDeadLetterQueue,

        @NotBlank
        String taskDeadLetterRoutingKey,

        @NotBlank
        String statusDeadLetterQueue,

        @NotBlank
        String statusDeadLetterRoutingKey,

        @NotNull
        Duration confirmTimeout,

        @Min(1)
        int taskQueueMaxLength,

        @Min(1)
        int statusQueueMaxLength,

        @NotNull
        DataSize maxMessageSize,

        @Min(1)
        int listenerPrefetch,

        @Min(1)
        int listenerConcurrency,

        @Min(1)
        int listenerMaxConcurrency,

        @Min(1)
        int statusRetryMaxAttempts,

        @NotNull
        Duration statusRetryInitialInterval,

        @NotNull
        Duration statusRetryMaxInterval,

        @NotNull
        Duration outboxPublishInterval,

        @Min(1)
        int outboxBatchSize,

        @Min(1)
        int outboxMaxAttempts,

        @NotNull
        Duration outboxInitialBackoff,

        @NotNull
        Duration outboxMaxBackoff,

        @NotNull
        Duration outboxClaimTimeout
) {
    public MessagingProperties {
        requirePositive(confirmTimeout, "Таймаут publisher confirm");
        requirePositive(statusRetryInitialInterval, "Начальный retry-интервал статуса");
        requirePositive(statusRetryMaxInterval, "Максимальный retry-интервал статуса");
        requirePositive(outboxPublishInterval, "Интервал публикации outbox");
        requirePositive(outboxInitialBackoff, "Начальный backoff outbox");
        requirePositive(outboxMaxBackoff, "Максимальный backoff outbox");
        requirePositive(outboxClaimTimeout, "Время аренды outbox");

        if (maxMessageSize != null && maxMessageSize.toBytes() <= 0) {
            throw new IllegalArgumentException(
                    "Максимальный размер сообщения должен быть положительным"
            );
        }
        if (listenerMaxConcurrency < listenerConcurrency) {
            throw new IllegalArgumentException(
                    "Максимальная concurrency не должна быть меньше начальной"
            );
        }
        if (statusRetryMaxInterval != null
                && statusRetryInitialInterval != null
                && statusRetryMaxInterval.compareTo(
                statusRetryInitialInterval
        ) < 0) {
            throw new IllegalArgumentException(
                    "Максимальный retry-интервал не должен быть меньше начального"
            );
        }
        if (outboxMaxBackoff != null
                && outboxInitialBackoff != null
                && outboxMaxBackoff.compareTo(outboxInitialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "Максимальный backoff outbox не должен быть меньше начального"
            );
        }
    }

    private static void requirePositive(Duration value, String field) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(
                    field + " должен быть положительным"
            );
        }
    }
}
