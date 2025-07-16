package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockAmountRevokedEventPayload {
    private String uuid;
    @JsonProperty("user_uuid")
    private String userUuid;
    @JsonProperty("user_name")
    private String userName;
    @JsonProperty("node_uuid") 
    private String nodeUuid;
}
