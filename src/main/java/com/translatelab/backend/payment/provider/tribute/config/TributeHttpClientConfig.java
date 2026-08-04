package com.translatelab.backend.payment.provider.tribute.config;

import com.translatelab.backend.config.TributeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributeHttpClientConfig {

    private static final String TRIBUTE_API_KEY_HEADER = "Api-Key";

    @Bean(name = "tributeRestClient")
    public RestClient tributeRestClient(
            TributeProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                properties.readTimeout()
        );

        return restClientBuilder
                .clone()
                .baseUrl(properties.baseUrl())
                .defaultHeader(
                        TRIBUTE_API_KEY_HEADER,
                        properties.apiKey()
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .requestFactory(requestFactory)
                .build();
    }
}
