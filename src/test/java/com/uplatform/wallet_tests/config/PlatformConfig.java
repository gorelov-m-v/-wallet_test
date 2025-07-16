package com.uplatform.wallet_tests.config;

import lombok.Data;

@Data
public class PlatformConfig {
    private String currency;
    private String country;
    private String nodeId;
    private String groupId;
}