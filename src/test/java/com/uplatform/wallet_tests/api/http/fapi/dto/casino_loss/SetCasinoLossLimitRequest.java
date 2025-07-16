package com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss;

import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetCasinoLossLimitRequest {
    private String amount;
    private String currency;
    private NatsLimitIntervalType type;
    private int startedAt;
}