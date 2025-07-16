package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class LimitKafkaClient extends AbstractKafkaClient {

    public LimitKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        super(kafkaBackgroundConsumer, configProvider);
    }

    public LimitMessage expectLimitMessage(
            String playerId,
            String limitType,
            String currencyCode,
            String amount
    ) {
        Map<String, String> filter = Map.of(
                "playerId", playerId,
                "limitType", limitType,
                "currencyCode", currencyCode,
                "amount", amount
        );

        String desc = String.format(
                "LimitMessage {playerId=%s, limitType=%s, currencyCode=%s, amount=%s}",
                playerId, limitType, currencyCode, amount
        );
        log.debug("Waiting for Kafka LimitMessage {}", desc);

        return expectMessage(filter, LimitMessage.class);
    }

    public LimitMessage expectUniqueLimitMessage(
            String playerId,
            String limitType,
            String currencyCode,
            String amount
    ) {
        Map<String, String> filter = Map.of(
                "playerId", playerId,
                "limitType", limitType,
                "currencyCode", currencyCode,
                "amount", amount
        );

        String desc = String.format(
                "LimitMessage {playerId=%s, limitType=%s, currencyCode=%s, amount=%s}",
                playerId, limitType, currencyCode, amount
        );
        log.debug("Waiting for unique Kafka LimitMessage {}", desc);

        return expectUniqueMessage(filter, LimitMessage.class);
    }
}
