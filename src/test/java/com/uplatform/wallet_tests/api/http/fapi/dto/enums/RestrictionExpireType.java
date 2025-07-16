package com.uplatform.wallet_tests.api.http.fapi.dto.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RestrictionExpireType {
    DAY("day"),
    THREE_DAYS("three-days"),
    WEEK("week"),
    MONTH("month"),
    THREE_MONTHS("three-month"),
    SIX_MONTHS("six-month");

    @JsonValue
    private final String jsonValue;

    public static RestrictionExpireType fromValue(String value) {
        for (RestrictionExpireType type : values()) {
            if (type.jsonValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown expire type: " + value);
    }
}
