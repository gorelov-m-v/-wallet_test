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
public enum DirectionType {
    INCREASE("INCREASE"),
    DECREASE("DECREASE"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, DirectionType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(DirectionType::getValue, Function.identity()));

    @JsonCreator
    public static DirectionType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize DirectionType from null JSON value");
        }
        DirectionType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for DirectionType: " + value);
        }
        return result;
    }
}