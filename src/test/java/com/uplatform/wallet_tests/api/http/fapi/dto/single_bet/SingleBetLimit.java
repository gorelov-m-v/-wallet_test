package com.uplatform.wallet_tests.api.http.fapi.dto.single_bet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SingleBetLimit {

    @JsonProperty("id")
    private String id;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private boolean status;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("upcomingChanges")
    private List<UpcomingChange> upcomingChanges;

    @JsonProperty("deactivatedAt")
    private Integer deactivatedAt;

    @JsonProperty("required")
    private boolean required;

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
