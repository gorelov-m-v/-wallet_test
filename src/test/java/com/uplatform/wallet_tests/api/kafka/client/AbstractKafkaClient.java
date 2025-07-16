package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import org.opentest4j.AssertionFailedError;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractKafkaClient {

    protected final KafkaBackgroundConsumer kafkaBackgroundConsumer;
    protected final Duration defaultFindTimeout;

    protected AbstractKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        this.kafkaBackgroundConsumer = kafkaBackgroundConsumer;
        this.defaultFindTimeout = configProvider.getKafkaConfig().getFindMessageTimeout();
    }

    protected <T> T expectMessage(
            Map<String, String> filter,
            Class<T> messageClass
    ) {
        Duration timeout = this.defaultFindTimeout;
        String typeDescription = messageClass.getSimpleName();
        String searchDetails = buildSearchDetails(filter);

        return kafkaBackgroundConsumer.findMessage(filter, timeout, messageClass)
                .orElseThrow(() -> new AssertionFailedError(
                        String.format(
                                "Kafka message %s %s not found within %s. Filter: %s",
                                typeDescription,
                                searchDetails,
                                timeout,
                                filter
                        ),
                        filter,
                        String.format("Message '%s' not received", typeDescription)
                ));
    }

    protected <T> T expectUniqueMessage(
            Map<String, String> filter,
            Class<T> messageClass
    ) {
        T message = expectMessage(filter, messageClass);
        int count = kafkaBackgroundConsumer.countMessages(filter, messageClass);
        if (count != 1) {
            String typeDescription = messageClass.getSimpleName();
            String searchDetails = buildSearchDetails(filter);
            throw new AssertionFailedError(
                    String.format(
                            "Kafka message %s %s expected once but found %d. Filter: %s",
                            typeDescription,
                            searchDetails,
                            count,
                            filter
                    ),
                    filter,
                    String.format("Message '%s' is not unique", typeDescription)
            );
        }
        return message;
    }

    protected String buildSearchDetails(Map<String, String> filter) {
        return filter.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
