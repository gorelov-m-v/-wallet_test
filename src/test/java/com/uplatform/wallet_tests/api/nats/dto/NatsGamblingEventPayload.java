package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsMessageName;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsTransactionDirection;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class NatsGamblingEventPayload {

    private String uuid;

    @JsonProperty("bet_uuid")
    private String betUuid;

    @JsonProperty("game_session_uuid")
    private String gameSessionUuid;

    @JsonProperty("provider_round_id")
    private String providerRoundId;

    private String currency;

    private BigDecimal amount;

    private NatsGamblingTransactionType type;

    @JsonProperty("provider_round_closed")
    private boolean providerRoundClosed;

    private NatsMessageName message;

    @JsonProperty("created_at")
    private Long createdAt;

    private NatsTransactionDirection direction;

    private NatsGamblingTransactionOperation operation;

    @JsonProperty("node_uuid")
    private String nodeUuid;

    @JsonProperty("game_uuid")
    private String gameUuid;

    @JsonProperty("provider_uuid")
    private String providerUuid;

    @JsonProperty("wagered_deposit_info")
    private List<Map<String, Object>> wageredDepositInfo;

    @JsonProperty("currency_conversion_info")
    private CurrencyConversionInfo currencyConversionInfo;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @NoArgsConstructor
    public static class CurrencyConversionInfo {
        @JsonProperty("game_amount")
        private BigDecimal gameAmount;

        @JsonProperty("game_currency")
        private String gameCurrency;

        @JsonProperty("currency_rates")
        private List<CurrencyRate> currencyRates;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @NoArgsConstructor
    public static class CurrencyRate {
        @JsonProperty("base_currency")
        private String baseCurrency;

        @JsonProperty("quote_currency")
        private String quoteCurrency;

        private String value;

        @JsonProperty("updated_at")
        private Long updatedAt;
    }
}