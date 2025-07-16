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
public enum CouponStatus {

    CREATED(1, "Created"),
    DECLINED(2, "Declined"),
    ACCEPTED(3, "Accepted"),
    WIN(4, "Win"),
    LOSS(5, "Loss"),
    REFUND(6, "Refund");

    @JsonValue
    private final int dbValue;
    private final String description;

    private static final Map<Integer, CouponStatus> valueMap = Arrays.stream(values())
            .collect(Collectors.toMap(CouponStatus::getDbValue, Function.identity()));

    /**
     * Used by the JPA converter (returns Optional).
     */
    public static Optional<CouponStatus> findByDbValue(Integer dbValue) {
        if (dbValue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(valueMap.get(dbValue));
    }

    /**
     * Used by Jackson to deserialize from numeric JSON value.
     */
    @JsonCreator
    public static CouponStatus fromDbValue(Integer dbValue) {
        if (dbValue == null) {
            throw new IllegalArgumentException("Null value received for CouponStatus");
        }
        CouponStatus result = valueMap.get(dbValue);
        if (result == null) {
            throw new IllegalArgumentException("Unknown database value for CouponStatus: " + dbValue);
        }
        return result;
    }

    public byte getDbValueAsByte() {
        return (byte) this.dbValue;
    }
}