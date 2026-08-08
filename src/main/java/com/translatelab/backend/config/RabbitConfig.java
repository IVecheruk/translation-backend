package com.translatelab.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public DirectExchange translationExchange(
            MessagingProperties properties
    ) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    public DirectExchange translationDeadLetterExchange(
            MessagingProperties properties
    ) {
        return new DirectExchange(
                properties.deadLetterExchange(),
                true,
                false
        );
    }

    @Bean
    public Queue translationQueue(MessagingProperties properties) {
        return QueueBuilder
                .durable(properties.queue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(
                        properties.taskDeadLetterRoutingKey()
                )
                .maxLength(properties.taskQueueMaxLength())
                .withArgument("x-overflow", "reject-publish-dlx")
                .build();
    }

    @Bean
    public Binding translationBinding(
            @Qualifier("translationQueue") Queue translationQueue,
            @Qualifier("translationExchange")
            DirectExchange translationExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder
                .bind(translationQueue)
                .to(translationExchange)
                .with(properties.routingKey());
    }

    @Bean
    public Queue translationStatusQueue(
            MessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.statusQueue())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(
                        properties.statusDeadLetterRoutingKey()
                )
                .maxLength(properties.statusQueueMaxLength())
                .withArgument("x-overflow", "reject-publish-dlx")
                .build();
    }

    @Bean
    public Binding translationStatusBinding(
            @Qualifier("translationStatusQueue") Queue translationStatusQueue,
            @Qualifier("translationExchange")
            DirectExchange translationExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder
                .bind(translationStatusQueue)
                .to(translationExchange)
                .with(properties.statusRoutingKey());
    }

    @Bean
    public Queue translationTaskDeadLetterQueue(
            MessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.taskDeadLetterQueue())
                .build();
    }

    @Bean
    public Binding translationTaskDeadLetterBinding(
            @Qualifier("translationTaskDeadLetterQueue")
            Queue deadLetterQueue,
            @Qualifier("translationDeadLetterExchange")
            DirectExchange deadLetterExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.taskDeadLetterRoutingKey());
    }

    @Bean
    public Queue translationStatusDeadLetterQueue(
            MessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.statusDeadLetterQueue())
                .build();
    }

    @Bean
    public Binding translationStatusDeadLetterBinding(
            @Qualifier("translationStatusDeadLetterQueue")
            Queue deadLetterQueue,
            @Qualifier("translationDeadLetterExchange")
            DirectExchange deadLetterExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.statusDeadLetterRoutingKey());
    }

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
