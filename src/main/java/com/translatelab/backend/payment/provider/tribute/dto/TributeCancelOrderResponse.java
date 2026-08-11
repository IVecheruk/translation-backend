package com.translatelab.backend.payment.provider.tribute.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TributeCancelOrderResponse(boolean success) {}
