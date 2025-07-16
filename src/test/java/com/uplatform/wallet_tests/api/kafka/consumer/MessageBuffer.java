package com.uplatform.wallet_tests.api.kafka.consumer;

import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageBuffer {

    private final int bufferSize;
    private final List<String> listenTopicSuffixes;
    private final String topicPrefix;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<ConsumerRecord<String, String>>> buffers = new ConcurrentHashMap<>();
    private List<String> fullListeningTopics;

    public MessageBuffer(
            EnvironmentConfigurationProvider configProvider
    ) {
        this.bufferSize = configProvider.getKafkaConfig().getBufferSize();
        this.topicPrefix = configProvider.getEnvironmentConfig().getTopicPrefix();
        this.listenTopicSuffixes = configProvider.getKafkaConfig().getListenTopicSuffixes();
    }

    @PostConstruct
    public void initialize() {
        this.fullListeningTopics = listenTopicSuffixes.stream()
                .map(suffix -> topicPrefix + suffix)
                .collect(Collectors.toList());
        this.fullListeningTopics.forEach(topic -> buffers.put(topic, new ConcurrentLinkedDeque<>()));
    }

    public void addRecord(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        ConcurrentLinkedDeque<ConsumerRecord<String, String>> buffer = buffers.get(topic);

        if (buffer != null) {
            buffer.addLast(record);
            while (bufferSize > 0 && buffer.size() > bufferSize) {
                ConsumerRecord<String, String> removed = buffer.pollFirst();
                if (removed != null) {
                    log.warn("Buffer overflow: Removed oldest message [Topic: {}, Offset: {}]. Buffer size now: {}",
                            topic,
                            removed.offset(),
                            buffer.size());
                } else {
                    break;
                }
            }
        } else {
            log.error("Received message for unexpected/unconfigured topic buffer: {}. Message ignored. Ensure this topic is in 'listenTopicSuffixes'. Listening to: {}",
                    topic,
                    fullListeningTopics);
        }
    }

    public ConcurrentLinkedDeque<ConsumerRecord<String, String>> getBufferForTopic(String topicName) {
        return buffers.get(topicName);
    }

    public boolean isTopicConfigured(String topicName) {
        return buffers.containsKey(topicName);
    }

    public List<String> getConfiguredTopics() {
        return this.fullListeningTopics;
    }

    public void clearAllBuffers() {
        buffers.values().forEach(ConcurrentLinkedDeque::clear);
        log.info("All message buffers cleared.");
    }

    public void clearBuffer(String topicName) {
        ConcurrentLinkedDeque<ConsumerRecord<String, String>> buffer = buffers.get(topicName);
        if (buffer != null) {
            buffer.clear();
        } else {
            log.warn("Attempted to clear buffer for unconfigured topic: {}", topicName);
        }
    }
}
