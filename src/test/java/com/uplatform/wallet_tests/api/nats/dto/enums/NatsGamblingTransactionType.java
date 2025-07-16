package com.uplatform.wallet_tests.api.nats.dto.enums;

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
public enum NatsGamblingTransactionType {

    TYPE_BET("TYPE_BET"),
    TYPE_TIPS("TYPE_TIPS"),
    TYPE_FREESPIN("TYPE_FREESPIN"),
    TYPE_WIN("TYPE_WIN"),
    TYPE_JACKPOT("TYPE_JACKPOT"),
    TYPE_REFUND("TYPE_REFUND"),
    TYPE_ROLLBACK("TYPE_ROLLBACK"),
    TYPE_TOURNAMENT("TYPE_TOURNAMENT"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, NatsGamblingTransactionType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsGamblingTransactionType::getValue, Function.identity()));

    @JsonCreator
    public static NatsGamblingTransactionType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize NatsGamblingTransactionType from null JSON value");
        }
        NatsGamblingTransactionType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for NatsGamblingTransactionType: " + value);
        }
        return result;
    }
}