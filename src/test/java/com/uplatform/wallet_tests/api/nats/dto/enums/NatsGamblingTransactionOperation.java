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
public enum NatsGamblingTransactionOperation {

    BET("bet"),
    TIPS("tips"),
    FREESPIN("free_spin"),
    WIN("win"),
    JACKPOT("jackpot"),
    REFUND("refund"),
    ROLLBACK("rollback"),
    TOURNAMENT("tournament"),
    UNKNOWN("unknown"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, NatsGamblingTransactionOperation> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsGamblingTransactionOperation::getValue, Function.identity()));

    @JsonCreator
    public static NatsGamblingTransactionOperation fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize NatsGamblingTransactionOperation from null JSON value");
        }
        NatsGamblingTransactionOperation result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for NatsGamblingTransactionOperation: " + value);
        }
        return result;
    }
}