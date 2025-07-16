package com.uplatform.wallet_tests.api.http.fapi.dto.player_restrictions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
public class PlayerRestrictionResponse {

    private String id;
    private Long startedAt;
    private List<UpcomingChange> upcomingChanges;
    private Instant expiresAt;
    private Instant deactivatedAt;

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