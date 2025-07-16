package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.api.kafka.dto.GameSessionStartMessage;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GameSessionKafkaClient extends AbstractKafkaClient {

    public GameSessionKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        super(kafkaBackgroundConsumer, configProvider);
    }

    public GameSessionStartMessage expectGameSessionStartMessage(String sessionUuid) {
        return expectMessage(
                Map.of("id", sessionUuid),
                GameSessionStartMessage.class
        );
    }

    public GameSessionStartMessage expectUniqueGameSessionStartMessage(String sessionUuid) {
        return expectUniqueMessage(
                Map.of("id", sessionUuid),
                GameSessionStartMessage.class
        );
    }
}
