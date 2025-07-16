package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class NatsPreventGambleSettedPayload {

    @JsonProperty("is_gambling_active")
    private boolean gamblingActive;

    @JsonProperty("is_betting_active")
    private boolean bettingActive;

    @JsonProperty("created_at")
    private long createdAt;
}