package com.uplatform.wallet_tests.api.nats.dto.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NatsLimitType {
    SINGLE_BET("single-bet"),
    CASINO_LOSS("casino-loss"),
    TURNOVER_FUNDS("turnover-of-funds"),
    UNKNOWN(null);

    private final String value;

    NatsLimitType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NatsLimitType fromValue(String v) {
        if (v == null) {
            return UNKNOWN;
        }
        for (NatsLimitType t : values()) {
            if (v.equalsIgnoreCase(t.value)) {
                return t;
            }
        }
        return UNKNOWN;
    }
}
