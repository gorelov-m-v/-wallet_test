package com.uplatform.wallet_tests.api.nats.dto.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NatsBlockAmountStatus {
    CREATED(2),
    REVOKED(3),
    UNKNOWN(0);

    private final int value;

    @JsonValue
    public int getValue() {
        return value;
    }

    public static NatsBlockAmountStatus fromValue(int value) {
        for (NatsBlockAmountStatus status : NatsBlockAmountStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
