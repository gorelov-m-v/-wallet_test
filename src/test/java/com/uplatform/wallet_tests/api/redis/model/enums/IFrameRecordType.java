package com.uplatform.wallet_tests.api.redis.model.enums;

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
public enum IFrameRecordType {

    BET("bet", "Bet"),
    WIN("win", "Win"),
    LOSS("loss", "Loss"),
    REFUND("refund", "Refund");

    private final String dbValue;
    private final String description;

    private static final Map<String, IFrameRecordType> valueMap = Arrays.stream(values())
            .collect(Collectors.toMap(e -> e.getDbValue().toLowerCase(),
                    Function.identity()));

    @JsonCreator
    public static IFrameRecordType fromDbValue(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        IFrameRecordType result = valueMap.get(dbValue.toLowerCase());
        if (result == null) {
            throw new IllegalArgumentException("Unknown database value for IFrameRecordType: " + dbValue);
        }
        return result;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}