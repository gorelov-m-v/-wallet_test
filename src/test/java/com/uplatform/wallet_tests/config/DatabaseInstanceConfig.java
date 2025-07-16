package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class DatabaseInstanceConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private int retryTimeoutSeconds;
    private long retryPollIntervalMs;
    private long retryPollDelayMs;
}