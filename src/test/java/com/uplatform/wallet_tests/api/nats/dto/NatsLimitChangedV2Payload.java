package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NatsLimitChangedV2Payload {

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("limits")
    private List<LimitDetail> limits;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LimitDetail {
        @JsonProperty("external_id")
        private String externalId;

        @JsonProperty("limit_type")
        private String limitType;

        @JsonProperty("interval_type")
        private String intervalType;

        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("currency_code")
        private String currencyCode;

        @JsonProperty("started_at")
        private Long startedAt;

        @JsonProperty("expires_at")
        private Long expiresAt;

        @JsonProperty("status")
        private Boolean status;
    }
}
