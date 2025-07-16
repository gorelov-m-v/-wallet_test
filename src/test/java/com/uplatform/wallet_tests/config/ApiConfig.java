package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class ApiConfig {
    private String baseUrl;
    private Credentials capCredentials;
    private ManagerConfig manager;
    private ConcurrencyConfig concurrency;
}