package com.uplatform.wallet_tests.config;

import lombok.Data;

import java.util.List;

@Data
public class NatsConfig {
    private List<String> hosts;
    private String streamName;
    private int subscriptionRetryCount;
    private long subscriptionRetryDelayMs;
    private long connectReconnectWaitSeconds;
    private int connectMaxReconnects;
    private long searchTimeoutSeconds;
    private long subscriptionAckWaitSeconds;
    private long subscriptionInactiveThresholdSeconds;
    private int subscriptionBufferSize;
}