package com.uplatform.wallet_tests.api.kafka.config;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class SimpleKafkaTopicMappingRegistry implements KafkaTopicMappingRegistry {

    private final Map<Class<?>, String> topicMap;

    public SimpleKafkaTopicMappingRegistry(Map<Class<?>, String> topicMap) {
        this.topicMap = Map.copyOf(topicMap);
    }

    @Override
    public Optional<String> getTopicSuffixFor(Class<?> messageType) {
        String suffix = topicMap.get(messageType);
        return Optional.ofNullable(suffix);
    }
}