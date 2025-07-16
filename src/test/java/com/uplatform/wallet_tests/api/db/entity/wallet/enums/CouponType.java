package com.uplatform.wallet_tests.api.db.entity.wallet.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum CouponType {

    SINGLE(1, "Single"),
    EXPRESS(2, "Express"),
    SYSTEM(3, "System");

    @JsonValue
    private final int dbValue;
    private final String description;

    private static final Map<Integer, CouponType> valueMap = Arrays.stream(values())
            .collect(Collectors.toMap(CouponType::getDbValue, Function.identity()));

    /**
     * Finds CouponType by database value (for JPA Converters).
     * @param dbValue the database value
     * @return Optional containing the Enum or empty Optional
     */
    public static Optional<CouponType> findByDbValue(Integer dbValue) {
        if (dbValue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(valueMap.get(dbValue));
    }

    /**
     * Creates CouponType from JSON/database value (for Jackson @JsonCreator).
     * @param dbValue the database value from JSON
     * @return the matching Enum
     * @throws IllegalArgumentException if value is null or not found
     */
    @JsonCreator
    public static CouponType fromDbValue(Integer dbValue) {
        if (dbValue == null) {
            throw new IllegalArgumentException("Cannot deserialize CouponType from null JSON value");
        }
        CouponType result = valueMap.get(dbValue);
        if (result == null) {
            throw new IllegalArgumentException("Unknown numeric value for CouponType: " + dbValue);
        }
        return result;
    }

    public byte getDbValueAsByte() {
        return (byte) this.dbValue;
    }
}