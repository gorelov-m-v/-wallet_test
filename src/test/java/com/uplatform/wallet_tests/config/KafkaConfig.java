package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Duration;
import java.util.List;

@Data
public class KafkaConfig {
    @JsonProperty("bootstrapServer")
    private String bootstrapServers;
    private String groupId;
    private List<String> listenTopicSuffixes;

    private int bufferSize;
    private Duration findMessageTimeout;
    private Duration findMessageSleepInterval;
    private Duration pollDuration;
    private boolean seekToEndOnStart;
    private Duration shutdownTimeout;
    private String autoOffsetReset;
    private boolean enableAutoCommit;
}