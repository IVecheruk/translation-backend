package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UsageConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(UsageConfig.class)
                    .withPropertyValues(
                            "app.usage.reservation-ttl=15m",
                            "app.usage.cleanup-interval=1m",
                            "app.usage.cleanup-batch-size=100"
                    );

    @Test
    void shouldBindUsagePropertiesAndProvideUtcClock() {
        contextRunner.run(context -> {
            assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(UsageProperties.class)
                    .hasSingleBean(Clock.class)
                    .hasSingleBean(
                            ScheduledAnnotationBeanPostProcessor.class
                    );

            UsageProperties properties =
                    context.getBean(UsageProperties.class);
            Clock clock = context.getBean(Clock.class);

            assertThat(properties.reservationTtl())
                    .isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.cleanupInterval())
                    .isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.cleanupBatchSize())
                    .isEqualTo(100);
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }
}
