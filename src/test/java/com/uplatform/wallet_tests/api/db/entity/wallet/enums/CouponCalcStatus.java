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
public enum CouponCalcStatus {

    NO(1, "Not Calculated"),
    CALCULATED(2, "Calculated"),
    RECALCULATED(3, "Recalculated");

    @JsonValue
    private final int dbValue;
    private final String description;

    private static final Map<Integer, CouponCalcStatus> valueMap = Arrays.stream(values())
            .collect(Collectors.toMap(CouponCalcStatus::getDbValue, Function.identity()));

    /**
     * Used by the JPA converter (returns Optional).
     */
    public static Optional<CouponCalcStatus> findByDbValue(Integer dbValue) {
        if (dbValue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(valueMap.get(dbValue));
    }

    /**
     * Used by Jackson to deserialize from numeric JSON value.
     */
    @JsonCreator
    public static CouponCalcStatus fromDbValue(Integer dbValue) {
        if (dbValue == null) {
            throw new IllegalArgumentException("Cannot deserialize CouponCalcStatus from null JSON value");
        }
        CouponCalcStatus result = valueMap.get(dbValue);
        if (result == null) {
            throw new IllegalArgumentException("Unknown numeric value for CouponCalcStatus: " + dbValue);
        }
        return result;
    }

    public byte getDbValueAsByte() {
        return (byte) this.dbValue;
    }
}