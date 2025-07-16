package com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Gender {
    MALE(1),
    FEMALE(2);

    private final int value;

    Gender(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static Gender fromValue(int value) {
        return Arrays.stream(Gender.values())
                .filter(gender -> gender.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown gender value: " + value));
    }
}