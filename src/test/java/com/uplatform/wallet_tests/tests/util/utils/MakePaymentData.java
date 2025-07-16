package com.uplatform.wallet_tests.tests.util.utils;

import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MakePaymentData {
    private NatsBettingTransactionOperation type;
    private Long time;
    private String playerId;
    private String currency;
    private String summ;
    private String totalCoef;
    private Long betId;
    private String sign;
    private Long champId;
    private NatsBettingCouponType couponType;
    private String betInfoCoef;
    private String champName;
    private Long dateStart;
    private String event;
    private String gameName;
    private String score;
    private String sportName;
}