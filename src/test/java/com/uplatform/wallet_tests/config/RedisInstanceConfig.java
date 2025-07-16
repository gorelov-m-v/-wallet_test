package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class RedisInstanceConfig {
    private String host;
    private int port;
    private int database;
    private String timeout;
    private LettucePoolConfig lettucePool;
}