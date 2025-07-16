package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Context {
    @JsonProperty("confirmationCode")
    private String confirmationCode;

    @JsonProperty("regType")
    private String regType;
}