package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.api.kafka.dto.PlayerAccountMessage;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PlayerAccountKafkaClient extends AbstractKafkaClient {

    public PlayerAccountKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        super(kafkaBackgroundConsumer, configProvider);
    }

    public PlayerAccountMessage expectPhoneConfirmationMessage(String phoneNumber) {
        String expectedKafkaPhoneValue = phoneNumber.substring(1);
        Map<String, String> filter = Map.of(
                "message.eventType", "player.confirmationPhone",
                "player.phone", expectedKafkaPhoneValue
        );
        return expectMessage(filter, PlayerAccountMessage.class);
    }

    public PlayerAccountMessage expectUniquePhoneConfirmationMessage(String phoneNumber) {
        String expectedKafkaPhoneValue = phoneNumber.substring(1);
        Map<String, String> filter = Map.of(
                "message.eventType", "player.confirmationPhone",
                "player.phone", expectedKafkaPhoneValue
        );
        return expectUniqueMessage(filter, PlayerAccountMessage.class);
    }

    public PlayerAccountMessage expectPlayerSignUpV2FullMessage(String phoneNumber) {
        String expectedKafkaPhoneValue = phoneNumber.substring(1);
        Map<String, String> filter = Map.of(
                "message.eventType", "player.signUpV2Full",
                "player.phone", expectedKafkaPhoneValue
        );
        return expectMessage(filter, PlayerAccountMessage.class);
    }

    public PlayerAccountMessage expectUniquePlayerSignUpV2FullMessage(String phoneNumber) {
        String expectedKafkaPhoneValue = phoneNumber.substring(1);
        Map<String, String> filter = Map.of(
                "message.eventType", "player.signUpV2Full",
                "player.phone", expectedKafkaPhoneValue
        );
        return expectUniqueMessage(filter, PlayerAccountMessage.class);
    }

    public PlayerAccountMessage expectEmailConfirmationMessage(String email) {
        Map<String, String> filter = Map.of(
                "message.eventType", "player.confirmationEmail",
                "player.email", email
        );
        return expectMessage(filter, PlayerAccountMessage.class);
    }

    public PlayerAccountMessage expectUniqueEmailConfirmationMessage(String email) {
        Map<String, String> filter = Map.of(
                "message.eventType", "player.confirmationEmail",
                "player.email", email
        );
        return expectUniqueMessage(filter, PlayerAccountMessage.class);
    }
}
