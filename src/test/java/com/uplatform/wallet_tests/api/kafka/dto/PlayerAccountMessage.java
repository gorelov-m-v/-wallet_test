package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAccountMessage {

    @JsonProperty("message")
    private PlayerEventMessage message;

    @JsonProperty("player")
    private PlayerInfo player;

    @JsonProperty("context")
    private Context context;
}