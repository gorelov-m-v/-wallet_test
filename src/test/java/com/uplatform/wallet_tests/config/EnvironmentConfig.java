package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentConfig {
    private String name;
    private ApiConfig api;
    private PlatformConfig platform;
    private Map<String, DatabaseInstanceConfig> databases;
    private RedisSettings redis;
    private KafkaConfig kafka;
    private NatsConfig nats;

    public String getTopicPrefix() {
        return name + "_";
    }

    public String getNatsStreamPrefix() {
        return name + "_";
    }
}

