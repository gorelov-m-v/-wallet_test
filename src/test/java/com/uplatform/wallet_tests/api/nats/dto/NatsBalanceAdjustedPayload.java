package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class NatsBalanceAdjustedPayload {
    private String uuid;

    @JsonProperty("currenc")
    private String currency;

    private BigDecimal amount;

    @JsonProperty("operation_type")
    private int operationType;

    private int direction;

    private int reason;

    private String comment;

    @JsonProperty("user_uuid")
    private String userUuid;

    @JsonProperty("user_name")
    private String userName;
}