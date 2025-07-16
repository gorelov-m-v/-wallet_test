package com.uplatform.wallet_tests.api.nats.dto.enums;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NatsLimitIntervalType {
    @JsonProperty("daily")   DAILY("daily"),
    @JsonProperty("weekly")  WEEKLY("weekly"),
    @JsonProperty("monthly") MONTHLY("monthly");

    private final String value;

    NatsLimitIntervalType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
