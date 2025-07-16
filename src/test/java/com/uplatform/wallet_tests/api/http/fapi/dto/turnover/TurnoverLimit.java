package com.uplatform.wallet_tests.api.http.fapi.dto.turnover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurnoverLimit {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private boolean status;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("spent")
    private BigDecimal spent;

    @JsonProperty("rest")
    private BigDecimal rest;

    @JsonProperty("startedAt")
    private Integer startedAt;

    @JsonProperty("expiresAt")
    private Integer expiresAt;

    @JsonProperty("deactivatedAt")
    private Integer deactivatedAt;

    @JsonProperty("required")
    private boolean required;

    @JsonProperty("upcomingChanges")
    private List<UpcomingChange> upcomingChanges;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpcomingChange {
        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("effectiveAt")
        private Long effectiveAt;
    }
}
