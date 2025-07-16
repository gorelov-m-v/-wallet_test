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
public enum ReasonType {
    MALFUNCTION("MALFUNCTION"),
    OPERATIONAL_MISTAKE("OPERATIONAL_MISTAKE"),
    BALANCE_CORRECTION("BALANCE_CORRECTION"),
    UNKNOWN("UNKNOWN"),
    EMPTY("");

    @JsonValue
    private final String value;

    private static final Map<String, ReasonType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(ReasonType::getValue, Function.identity()));

    @JsonCreator
    public static ReasonType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize ReasonType from null JSON value");
        }
        ReasonType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for ReasonType: " + value);
        }
        return result;
    }
}