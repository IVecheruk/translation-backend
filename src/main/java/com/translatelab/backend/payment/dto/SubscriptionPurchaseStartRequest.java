package com.translatelab.backend.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubscriptionPurchaseStartRequest(

        @JsonProperty("plan_code")
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,31}$")
        String planCode
) {

    public SubscriptionPurchaseStartRequest {
        if (planCode != null) {
            planCode = planCode.strip();
        }
    }
}