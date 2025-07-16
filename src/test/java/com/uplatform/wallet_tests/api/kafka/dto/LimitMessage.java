package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LimitMessage {

    @JsonProperty("limitType")
    private String limitType;

    @JsonProperty("intervalType")
    private String intervalType;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("currencyCode")
    private String currencyCode;

    @JsonProperty("id")
    private String id;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("status")
    private Boolean status;

    @JsonProperty("startedAt")
    private Long startedAt;

    @JsonProperty("expiresAt")
    private Long expiresAt;

    @JsonProperty("eventType")
    private String eventType;
}
