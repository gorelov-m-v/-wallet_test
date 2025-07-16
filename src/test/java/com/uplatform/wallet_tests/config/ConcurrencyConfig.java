package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class ConcurrencyConfig {
    private long requestTimeoutMs;
    private int defaultRequestCount;
}