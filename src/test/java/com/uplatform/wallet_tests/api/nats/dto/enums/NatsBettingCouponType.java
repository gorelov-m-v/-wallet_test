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
public enum NatsBettingCouponType {

    SINGLE("Single"),
    EXPRESS("Express"),
    SYSTEM("System"),
    UNKNOWN("unknown"),
    EMPTY("");

    @JsonValue
    private final String value;
    private static final Map<String, NatsBettingCouponType> valueMap =
            Arrays.stream(values())
                    .collect(Collectors.toMap(NatsBettingCouponType::getValue, Function.identity()));

    @JsonCreator
    public static NatsBettingCouponType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot deserialize NatsBettingCouponType from null JSON value");
        }
        NatsBettingCouponType result = valueMap.get(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value for NatsBettingCouponType: " + value);
        }
        return result;
    }
}