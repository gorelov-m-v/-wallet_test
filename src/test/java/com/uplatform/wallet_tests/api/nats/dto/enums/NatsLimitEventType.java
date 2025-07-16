package com.uplatform.wallet_tests.api.nats.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NatsLimitEventType {

    AMOUNT_UPDATED("amount_updated"),
    SPENT_RESETTED("spent_resetted"),
    CREATED("created"),
    UPDATED("updated"),
    UNKNOWN("unknown");

    private final String value;
}