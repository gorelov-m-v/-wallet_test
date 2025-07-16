package com.uplatform.wallet_tests.api.nats.dto.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NatsBlockAmountType {
    MANUAL(3),
    UNKNOWN(0);

    private final int value;

    @JsonValue
    public int getValue() {
        return value;
    }

    public static NatsBlockAmountType fromValue(int value) {
        for (NatsBlockAmountType type : NatsBlockAmountType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
