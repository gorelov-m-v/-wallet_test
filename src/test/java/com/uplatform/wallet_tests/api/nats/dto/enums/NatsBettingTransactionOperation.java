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
public enum NatsBettingTransactionOperation {

    BET("bet"),
    WIN("win"),
    LOSS("loss"),
    REFUND("refund"),
    UNKNOWN("unknown"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, NatsBettingTransactionOperation> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsBettingTransactionOperation::getValue, Function.identity()));

    @JsonCreator
    public static NatsBettingTransactionOperation fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize NatsBettingTransactionOperation from null JSON value");
        }
        NatsBettingTransactionOperation result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for NatsBettingTransactionOperation: " + value);
        }
        return result;
    }
}