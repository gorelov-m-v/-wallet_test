package com.uplatform.wallet_tests.api.redis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletData {

    @JsonProperty("wallet_uuid")
    private String walletUUID;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("type")
    private int type;

    @JsonProperty("status")
    private int status;

}