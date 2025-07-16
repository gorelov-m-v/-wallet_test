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
public enum NatsMessageName {

    WALLET_GAME_TRANSACTION("wallet.gameTransaction"),
    UNKNOWN("unknown");

    @JsonValue
    private final String value;

    private static final Map<String, NatsMessageName> VALUE_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsMessageName::getValue, Function.identity()));

    @JsonCreator
    public static NatsMessageName fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize NatsMessageName from null JSON value");
        }
        NatsMessageName result = VALUE_MAP.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for NatsMessageName: " + value);
        }
        return result;
    }
}