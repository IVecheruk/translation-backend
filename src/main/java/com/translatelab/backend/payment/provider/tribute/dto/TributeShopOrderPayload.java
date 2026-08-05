package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TributeShopOrderPayload(

        UUID uuid,

        long amount,

        String currency,

        String status,

        @JsonProperty("isRecurrent")
        boolean recurrent,

        String period
) {

    public TributeShopOrderPayload()
}