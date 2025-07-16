package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class NatsBettingEventPayload {

    private String uuid;

    private NatsBettingTransactionOperation type;

    @JsonProperty("bet_id")
    private long betId;

    @JsonProperty("bet_info")
    private List<BetInfoDetail> betInfo;

    private BigDecimal amount;

    @JsonProperty("raw_amount")
    private BigDecimal rawAmount;

    @JsonProperty("total_coeff")
    private BigDecimal totalCoeff;

    private long time;

    @JsonProperty("created_at")
    private Long createdAt;

    @JsonProperty("wagered_deposit_info")
    private List<Object> wageredDepositInfo;
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    @NoArgsConstructor
    public static class BetInfoDetail {

        @JsonProperty("ChampName")
        private String champName;

        @JsonProperty("Coef")
        private BigDecimal coef;

        @JsonProperty("CouponType")
        private String couponType;

        @JsonProperty("Event")
        private String event;

        @JsonProperty("GameName")
        private String gameName;

        @JsonProperty("Score")
        private String score;

        @JsonProperty("SportName")
        private String sportName;

        @JsonProperty("ChampId")
        private Long champId;

        @JsonProperty("DateStart")
        private Long dateStart;

    }
}