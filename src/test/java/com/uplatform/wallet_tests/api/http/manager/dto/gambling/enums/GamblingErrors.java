package com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums;

import lombok.Getter;

@Getter
public enum GamblingErrors {
    MISSING_TOKEN(100, "Missing or invalid token"),
    VALIDATION_ERROR(103, "Validation error"),
    BUSINESS_LOGIC_ERROR(104, "Business logic error"),
    LIMIT_IS_OVER(105, "Limit is over"),
    PLAYER_BLOCKED(107, "Player is blocked"),
    REFUND_NOT_ALLOWED(107, "refund not allowed"),
    REFUND_ALREADY_HANDLED(107, "refund already handled"),
    ROLLBACK_ALREADY_HANDLED(107, "rollback already handled"),
    ROLLBACK_NOT_ALLOWED(107, "rollback not allowed");

    private final int code;
    private final String message;

    GamblingErrors(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
