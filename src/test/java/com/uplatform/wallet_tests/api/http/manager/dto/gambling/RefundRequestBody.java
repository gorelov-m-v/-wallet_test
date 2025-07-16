package com.uplatform.wallet_tests.api.http.manager.dto.gambling;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestBody {

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("playerId")
    private String playerId;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("sessionToken")
    private String sessionToken;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("gameUuid")
    private String gameUuid;

    @JsonProperty("betTransactionId")
    private String betTransactionId;

    @JsonProperty("roundId")
    private String roundId;

    @JsonProperty("roundClosed")
    private boolean roundClosed;
}