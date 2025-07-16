package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class RedisAggregateConfig {
    private int maxGamblingCount;
    private int maxIframeCount;
    private int retryAttempts;
    private long retryDelayMs;
}