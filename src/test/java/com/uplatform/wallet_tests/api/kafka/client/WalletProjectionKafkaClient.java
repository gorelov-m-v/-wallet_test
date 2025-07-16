package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WalletProjectionKafkaClient extends AbstractKafkaClient {

    public WalletProjectionKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        super(kafkaBackgroundConsumer, configProvider);
    }

    public WalletProjectionMessage expectWalletProjectionMessageBySeqNum(long seqNumber) {
        return expectMessage(
                Map.of("seq_number", String.valueOf(seqNumber)),
                WalletProjectionMessage.class
        );
    }

    public WalletProjectionMessage expectUniqueWalletProjectionMessageBySeqNum(long seqNumber) {
        return expectUniqueMessage(
                Map.of("seq_number", String.valueOf(seqNumber)),
                WalletProjectionMessage.class
        );
    }
}
