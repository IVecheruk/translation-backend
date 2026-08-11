package com.translatelab.backend.payment.provider.tribute.config;

import com.translatelab.backend.config.PaymentConfig;
import com.translatelab.backend.config.TributeProperties;
import com.translatelab.backend.payment.provider.tribute.TributeCheckoutMapper;
import com.translatelab.backend.payment.provider.tribute.TributePaymentCheckoutGateway;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookCommandMapper;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookPayloadDecoder;
import com.translatelab.backend.payment.provider.tribute.TributeWebhookSignatureVerifier;
import com.translatelab.backend.payment.provider.tribute.controller.TributeWebhookController;
import com.translatelab.backend.payment.provider.tribute.service.TributeWebhookService;
import com.translatelab.backend.payment.service.SubscriptionPurchaseCompletionService;
import com.translatelab.backend.payment.service.SubscriptionProviderLifecycleService;
import com.translatelab.backend.payment.service.SubscriptionPurchaseFailureService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TributeHttpClientConfigTest {

    private static final URI BASE_URL = URI.create(
            "https://tribute.tg/api/v1"
    );
    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT =
            Duration.ofSeconds(10);

    @Mock
    private RestClient.Builder originalBuilder;

    @Mock
    private RestClient.Builder clonedBuilder;

    @Mock
    private RestClient restClient;

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            PaymentConfig.class,
                            TributeHttpClientConfig.class,
                            TributePaymentCheckoutGateway.class,
                            TributeWebhookSignatureVerifier.class,
                            TributeWebhookPayloadDecoder.class,
                            TributeWebhookCommandMapper.class,
                            TributeWebhookService.class,
                            TributeWebhookController.class
                    )
                    .withBean(
                            TributeCheckoutMapper.class,
                            TributeCheckoutMapper::new
                    )
                    .withBean(
                            RestClient.Builder.class,
                            RestClient::builder
                    )
                    .withBean(
                            ObjectMapper.class,
                            () -> JsonMapper
                                    .builder()
                                    .findAndAddModules()
                                    .build()
                    )
                    .withBean(
                            SimpleMeterRegistry.class,
                            SimpleMeterRegistry::new
                    )
                    .withBean(
                            SubscriptionPurchaseCompletionService.class,
                            () -> mock(
                                    SubscriptionPurchaseCompletionService.class
                            )
                    )
                    .withBean(
                            SubscriptionProviderLifecycleService.class,
                            () -> mock(SubscriptionProviderLifecycleService.class)
                    )
                    .withBean(
                            SubscriptionPurchaseFailureService.class,
                            () -> mock(SubscriptionPurchaseFailureService.class)
                    )
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
    void shouldNotCreateClientWhileTributeIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean("tributeRestClient")
                    .doesNotHaveBean(
                            TributePaymentCheckoutGateway.class
                    )
                    .doesNotHaveBean(
                            TributeWebhookSignatureVerifier.class
                    )
                    .doesNotHaveBean(
                            TributeWebhookPayloadDecoder.class
                    )
                    .doesNotHaveBean(
                            TributeWebhookCommandMapper.class
                    )
                    .doesNotHaveBean(
                            TributeWebhookService.class
                    )
                    .doesNotHaveBean(
                            TributeWebhookController.class
                    );
        });
    }

    @Test
    void shouldCreateNamedClientOnlyWhenTributeIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.payment.tribute.enabled=true",
                        "app.payment.tribute.api-key=synthetic-test-key"
                )
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasBean("tributeRestClient")
                            .hasSingleBean(
                                    TributePaymentCheckoutGateway.class
                            )
                            .hasSingleBean(
                                    TributeWebhookSignatureVerifier.class
                            )
                            .hasSingleBean(
                                    TributeWebhookPayloadDecoder.class
                            )
                            .hasSingleBean(
                                    TributeWebhookCommandMapper.class
                            )
                            .hasSingleBean(
                                    TributeWebhookService.class
                            )
                            .hasSingleBean(
                                    TributeWebhookController.class
                            );
                    assertThat(context.getBean("tributeRestClient"))
                            .isInstanceOf(RestClient.class);
                });
    }

    @Test
    void shouldPreserveApiVersionWhenAppendingShopOrderPath() {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(
                BASE_URL.toString()
        );

        URI uri = factory.expand("/shop/orders", Map.of());

        assertEquals(
                URI.create(
                        "https://tribute.tg/api/v1/shop/orders"
                ),
                uri
        );
    }

    @Test
    void shouldBuildIsolatedClientWithSafeTransportSettings() {
        TributeProperties properties = new TributeProperties(
                true,
                BASE_URL,
                "synthetic-test-key",
                CONNECT_TIMEOUT,
                READ_TIMEOUT
        );
        given(originalBuilder.clone()).willReturn(clonedBuilder);
        given(clonedBuilder.baseUrl(BASE_URL)).willReturn(clonedBuilder);
        given(clonedBuilder.defaultHeader(
                anyString(),
                any(String[].class)
        )).willReturn(clonedBuilder);
        given(clonedBuilder.requestFactory(
                any(ClientHttpRequestFactory.class)
        )).willReturn(clonedBuilder);
        given(clonedBuilder.build()).willReturn(restClient);

        RestClient result = new TributeHttpClientConfig()
                .tributeRestClient(properties, originalBuilder);

        ArgumentCaptor<ClientHttpRequestFactory> factoryCaptor =
                ArgumentCaptor.forClass(ClientHttpRequestFactory.class);
        verify(originalBuilder).clone();
        verify(clonedBuilder).baseUrl(BASE_URL);
        verify(clonedBuilder).defaultHeader(
                "Api-Key",
                "synthetic-test-key"
        );
        verify(clonedBuilder).defaultHeader(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
        );
        verify(clonedBuilder).requestFactory(factoryCaptor.capture());
        verify(clonedBuilder).build();
        verifyNoInteractions(restClient);

        JdkClientHttpRequestFactory requestFactory =
                (JdkClientHttpRequestFactory) factoryCaptor.getValue();
        HttpClient httpClient = (HttpClient) ReflectionTestUtils
                .getField(requestFactory, "httpClient");
        Duration readTimeout = (Duration) ReflectionTestUtils
                .getField(requestFactory, "readTimeout");

        assertAll(
                () -> assertSame(restClient, result),
                () -> assertEquals(
                        CONNECT_TIMEOUT,
                        httpClient.connectTimeout().orElseThrow()
                ),
                () -> assertEquals(
                        HttpClient.Redirect.NEVER,
                        httpClient.followRedirects()
                ),
                () -> assertEquals(READ_TIMEOUT, readTimeout)
        );
    }
}
