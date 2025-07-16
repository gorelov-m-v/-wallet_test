package com.uplatform.wallet_tests.config;

import lombok.Data;

import java.util.Map;

@Data
public class RedisConfig {
    private RedisAggregateConfig aggregate;
    private Map<String, RedisInstanceConfig> instances;
}