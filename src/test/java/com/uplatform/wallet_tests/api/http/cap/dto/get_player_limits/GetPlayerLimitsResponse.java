package com.uplatform.wallet_tests.api.http.cap.dto.get_player_limits;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class GetPlayerLimitsResponse {

    private List<PlayerLimit> data;
    private int total;

    @Data
    @NoArgsConstructor
    public static class PlayerLimit {

        private String type;
        private boolean status;
        private String period;
        private String currency;
        private BigDecimal amount;
        @Nullable
        private BigDecimal rest;
        @Nullable
        private BigDecimal spent;
        private Long createdAt;
        @Nullable
        private Long deactivatedAt;
        private Integer startedAt;
        @Nullable
        private Integer expiresAt;
    }
}
