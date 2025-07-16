package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class LettucePoolConfig {
    private int maxActive;
    private int maxIdle;
    private int minIdle;
    private String shutdownTimeout;
}