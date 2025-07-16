package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerEventMessage {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("eventCreatedAt")
    private Long eventCreatedAt;
}
