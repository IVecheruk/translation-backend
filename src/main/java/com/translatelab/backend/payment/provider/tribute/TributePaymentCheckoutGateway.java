package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;
import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import com.translatelab.backend.payment.provider.PaymentCheckoutGateway;
import com.translatelab.backend.payment.provider.tribute.dto.TributeCreateOrderRequest;
import com.translatelab.backend.payment.provider.tribute.dto.TributeCreateOrderResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributePaymentCheckoutGateway
        implements PaymentCheckoutGateway {

    private static final String PROVIDER_CODE = "TRIBUTE";
    private static final String CREATE_ORDER_PATH = "/shop/orders";

    private final RestClient restClient;
    private final TributeCheckoutMapper mapper;

    public TributePaymentCheckoutGateway(
            @Qualifier("tributeRestClient")
            RestClient restClient,
            TributeCheckoutMapper mapper
    ) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public PaymentCheckoutResult createCheckout(
            PaymentCheckoutCreationCommand command
    ) {
        Objects.requireNonNull(
                command,
                "Команда создания checkout не должна быть null"
        );

        TributeCreateOrderRequest request =
                mapper.toCreateOrderRequest(command);

        TributeCreateOrderResponse response;

        try {
            response = restClient
                    .post()
                    .uri(CREATE_ORDER_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TributeCreateOrderResponse.class);
        } catch (RestClientException exception) {
            throw new PaymentProviderUnavailableException(
                    exception
            );
        }

        if (response == null) {
            throw new PaymentProviderUnavailableException();
        }

        return mapper.toCheckoutResult(response);
    }
}
