package com.translatelab.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UsageProperties.class)
public class UsageConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}