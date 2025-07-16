package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletProjectionMessage {

    @JsonProperty("type")
    private String type;

    @JsonProperty("seq_number")
    private long seqNumber;

    @JsonProperty("wallet_uuid")
    private String walletUuid;

    @JsonProperty("player_uuid")
    private String playerUuid;

    @JsonProperty("node_uuid")
    private String nodeUuid;

    @JsonProperty("payload")
    private String payload;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("seq_number_node_uuid")
    private String seqNumberNodeUuid;
}