package com.uplatform.wallet_tests.api.http.manager.dto.betting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum BettingErrorCode {

    SUCCESS(0, ""),
    ALREADY_PROCESSED_REQUEST_ID(2, "already processed reqId"),
    BALANCE_NOT_ENOUGH(3, "balance not enough"),
    COOLING_OFF_LIMIT_REACHED(5, "cooling off limit reached"),
    SINGLE_BET_LIMIT_REACHED(6, "single bet limit reached"),
    TURNOVER_LIMIT_REACHED(7, "turn over of funds limit reached"),
    LOSS_LIMIT_REACHED(8, "loss limit reached"),
    BETTING_IS_DISABLED(9, "betting is disabled"),
    NOT_FOUND(11, "bet not found"),

    UNKNOWN_ERROR(-1, "Unknown error");

    private final int code;
    private final String description;

    private static final Map<Integer, BettingErrorCode> codeMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(BettingErrorCode::getCode, Function.identity()));

    public static BettingErrorCode findByCode(int code) {
        return codeMap.getOrDefault(code, UNKNOWN_ERROR);
    }
}