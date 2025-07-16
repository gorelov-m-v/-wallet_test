package com.uplatform.wallet_tests.api.kafka.config;

import java.util.Optional;

public interface KafkaTopicMappingRegistry {
    Optional<String> getTopicSuffixFor(Class<?> messageType);
}