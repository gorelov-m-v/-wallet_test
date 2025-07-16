package com.uplatform.wallet_tests.api.http.fapi.dto.turnover;

import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetTurnoverLimitRequest {
    private String amount;
    private String currency;
    private NatsLimitIntervalType type;
    private int startedAt;
}