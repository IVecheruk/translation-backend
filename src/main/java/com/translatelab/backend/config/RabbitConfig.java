package com.translatelab.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    @Bean
    public DirectExchange translationExchange(
            MessagingProperties properties
    ) {
        return new DirectExchange(
                properties.exchange(),
                true,
                false
        );
    }

    @Bean
    public Queue translationQueue(
            MessagingProperties properties
    ) {
        return QueueBuilder
                .durable(properties.queue())
                .build();
    }

    @Bean
    public Binding translationBinding(
            @Qualifier("translationQueue") Queue translationQueue,
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
                .build();
    }

    @Bean
    public Binding translationStatusBinding(
            @Qualifier("translationStatusQueue") Queue translationStatusQueue,
            DirectExchange translationExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder
                .bind(translationStatusQueue)
                .to(translationExchange)
                .with(properties.statusRoutingKey());
    }

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
