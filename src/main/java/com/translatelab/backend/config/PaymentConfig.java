package com.translatelab.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        PaymentProperties.class,
        TributeProperties.class
})
public class PaymentConfig {
}
