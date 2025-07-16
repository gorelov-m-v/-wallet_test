package com.uplatform.wallet_tests.api.kafka.consumer;

import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.config.KafkaConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class KafkaPollingService {

    private final MessageBuffer messageBuffer;
    private final KafkaConfig kafkaConfig;

    private KafkaMessageListenerContainer<String, String> container;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private List<String> subscribedTopics = Collections.emptyList();

    public KafkaPollingService(
            @Lazy MessageBuffer messageBuffer,
            EnvironmentConfigurationProvider configProvider
    ) {
        this.messageBuffer = messageBuffer;
        this.kafkaConfig = configProvider.getKafkaConfig();
    }

    public void start(List<String> topicsToSubscribe) {
        if (topicsToSubscribe == null || topicsToSubscribe.isEmpty()) {
            log.warn("KafkaPollingService: No topics to subscribe to. Service will not start.");
            return;
        }
        if (running.getAndSet(true)) {
            log.warn("KafkaPollingService is already running.");
            return;
        }

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConfig.getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaConfig.isEnableAutoCommit());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_uncommitted");

        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        ContainerProperties cp = new ContainerProperties(topicsToSubscribe.toArray(new String[0]));
        cp.setGroupId(kafkaConfig.getGroupId());
        cp.setPollTimeout(kafkaConfig.getPollDuration().toMillis());
        cp.setMessageListener((MessageListener<String, String>) messageBuffer::addRecord);

        if (kafkaConfig.isSeekToEndOnStart()) {
            cp.setConsumerRebalanceListener(new ConsumerAwareRebalanceListener() {
                @Override
                public void onPartitionsAssigned(Consumer<?, ?> consumer, java.util.Collection<TopicPartition> partitions) {
                    consumer.seekToEnd(partitions);
                }
            });
        }

        container = new KafkaMessageListenerContainer<>(cf, cp);
        container.start();
        subscribedTopics = topicsToSubscribe;
        log.info("KafkaPollingService started. Listening to topics: {}", topicsToSubscribe);
    }

    @PreDestroy
    public void stop() {
        if (!running.getAndSet(false)) {
            log.warn("KafkaPollingService was not running or already stopping.");
            return;
        }

        if (container != null) {
            container.stop();
            container = null;
            log.info("KafkaPollingService stopped. Was listening to topics: {}", subscribedTopics);
        }
        subscribedTopics = Collections.emptyList();
    }

    public boolean isRunning() {
        return running.get() && container != null && container.isRunning();
    }
}