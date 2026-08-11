package com.translatelab.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitRetryConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RabbitRetryConfig.class
    );

    @Bean
    public RabbitListenerRetrySettingsCustomizer
            permanentMessageFailureRetryCustomizer() {
        return settings -> settings.getExceptionExcludes().addAll(
                java.util.List.of(
                        AmqpRejectAndDontRequeueException.class,
                        MessageConversionException.class
                )
        );
    }

    @Bean
    public MessageRecoverer messageRecoverer(
            MeterRegistry meterRegistry
    ) {
        Counter exhaustedCounter = Counter.builder(
                        "translation.rabbit.status.retry.exhausted"
                )
                .description(
                        "Количество сообщений статуса, исчерпавших retry"
                )
                .register(meterRegistry);

        return (message, cause) -> {
            exhaustedCounter.increment();
            LOGGER.warn(
                    "Сообщение статуса исчерпало retry и будет помещено в DLQ: {}",
                    cause.getClass().getSimpleName()
            );
            throw new AmqpRejectAndDontRequeueException(
                    "Исчерпаны попытки обработки статуса перевода",
                    cause
            );
        };
    }
}
