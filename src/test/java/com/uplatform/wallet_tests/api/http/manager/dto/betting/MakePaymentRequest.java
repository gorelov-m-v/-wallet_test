package com.uplatform.wallet_tests.api.http.manager.dto.betting;

import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MakePaymentRequest {
    private String sign;
    private long time;
    private NatsBettingTransactionOperation type;
    private String token2;
    private Long betId;
    private String betInfo;
    private String summ;
    private String totalCoef;

}