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
public enum NatsTransactionDirection {

    WITHDRAW("withdraw"),
    DEPOSIT("deposit"),
    UNKNOWN("unknown");

    @JsonValue
    private final String value;

    private static final Map<String, NatsTransactionDirection> VALUE_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsTransactionDirection::getValue, Function.identity()));

    @JsonCreator
    public static NatsTransactionDirection fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize NatsTransactionDirection from null JSON value");
        }
        NatsTransactionDirection result = VALUE_MAP.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for NatsTransactionDirection: " + value);
        }
        return result;
    }
}