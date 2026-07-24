package com.translatelab.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            Queue translationQueue,
            DirectExchange translationExchange,
            MessagingProperties properties
    ) {
        return BindingBuilder
                .bind(translationQueue)
                .to(translationExchange)
                .with(properties.routingKey());
    }

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}