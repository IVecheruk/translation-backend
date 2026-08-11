package com.translatelab.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(PaymentConfig.class)
                    .withPropertyValues(
                            "app.payment.purchase-intent-ttl=30m",
                            "app.payment.provider=TRIBUTE",
                            "app.payment.tribute.enabled=false",
                            "app.payment.tribute.base-url="
                                    + "https://tribute.tg/api/v1",
                            "app.payment.tribute.api-key=",
                            "app.payment.tribute.connect-timeout=3s",
                            "app.payment.tribute.read-timeout=10s"
                    );

    @Test
    void shouldBindPaymentProperties() {
        contextRunner.run(context -> {
            assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(PaymentProperties.class)
                    .hasSingleBean(TributeProperties.class);

            PaymentProperties properties =
                    context.getBean(PaymentProperties.class);

            assertThat(properties.purchaseIntentTtl())
                    .isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.provider()).isEqualTo("TRIBUTE");

            TributeProperties tributeProperties =
                    context.getBean(TributeProperties.class);

            assertThat(tributeProperties.enabled()).isFalse();
            assertThat(tributeProperties.baseUrl()).isEqualTo(
                    URI.create("https://tribute.tg/api/v1")
            );
            assertThat(tributeProperties.apiKey()).isEmpty();
            assertThat(tributeProperties.connectTimeout()).isEqualTo(
                    Duration.ofSeconds(3)
            );
            assertThat(tributeProperties.readTimeout()).isEqualTo(
                    Duration.ofSeconds(10)
            );
        });
    }

    @Test
    void shouldBindEnabledTributePropertiesWithSyntheticApiKey() {
        contextRunner
                .withPropertyValues(
                        "app.payment.tribute.enabled=true",
                        "app.payment.tribute.api-key=synthetic-test-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    TributeProperties properties =
                            context.getBean(TributeProperties.class);

                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.apiKey()).isEqualTo(
                            "synthetic-test-key"
                    );
                });
    }

    @Test
    void shouldRejectEnabledTributeWithoutApiKey() {
        contextRunner
                .withPropertyValues(
                        "app.payment.tribute.enabled=true",
                        "app.payment.tribute.api-key="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    IllegalArgumentException.class
                            )
                            .hasRootCauseMessage(
                                    "Для включения Tribute необходимо "
                                            + "настроить API-ключ"
                            );
                });
    }
}
