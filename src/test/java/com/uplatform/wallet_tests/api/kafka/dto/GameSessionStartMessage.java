package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameSessionStartMessage {

    private MessageDetails message;

    @JsonProperty("player_id")
    private String playerId;

    @JsonProperty("player_bonus_uuid")
    private String playerBonusUuid;

    @JsonProperty("node_id")
    private String nodeId;

    private String id;

    private String ip;

    @JsonProperty("provider_id")
    private String providerId;

    @JsonProperty("provider_external_id")
    private String providerExternalId;

    @JsonProperty("game_type_name")
    private String gameTypeName;

    @JsonProperty("game_id")
    private String gameId;

    @JsonProperty("game_external_id")
    private String gameExternalId;

    private String currency;

    @JsonProperty("start_date")
    private Long startDate;

    @JsonProperty("game_mode")
    private String gameMode;

    private String useragent;

    @JsonProperty("wallet_uuid")
    private String walletUuid;

    @JsonProperty("secret_key")
    private String secretKey;

    @JsonProperty("category_id")
    private String categoryId;

    @JsonProperty("type_id")
    private String typeId;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageDetails {
        private String eventType;
    }
}