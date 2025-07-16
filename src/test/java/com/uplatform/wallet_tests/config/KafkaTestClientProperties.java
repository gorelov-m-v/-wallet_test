package com.uplatform.wallet_tests.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.kafka.test-client")
@Data
public class KafkaTestClientProperties {
    private int bufferSize;
    private Duration findMessageTimeout;
    private Duration findMessageSleepInterval;
    private boolean seekToEndOnStart;
    private Duration shutdownTimeout;
    private String autoOffsetReset;
    private boolean enableAutoCommit;
}