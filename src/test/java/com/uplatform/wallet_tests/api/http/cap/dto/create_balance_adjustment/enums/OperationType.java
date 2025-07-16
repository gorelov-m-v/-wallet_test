package com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum OperationType {
    CORRECTION("CORRECTION"),
    DEPOSIT("DEPOSIT"),
    WITHDRAWAL("WITHDRAWAL"),
    GIFT("GIFT"),
    REFERRAL_COMMISSION("REFERRAL_COMMISSION"),
    CASHBACK("CASHBACK"),
    TOURNAMENT_PRIZE("TOURNAMENT_PRIZE"),
    JACKPOT("JACKPOT"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, OperationType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(OperationType::getValue, Function.identity()));

    @JsonCreator
    public static OperationType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize OperationType from null JSON value");
        }
        OperationType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for OperationType: " + value);
        }
        return result;
    }
}