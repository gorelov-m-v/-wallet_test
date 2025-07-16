package com.uplatform.wallet_tests.api.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.config.NatsConfig;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

import com.uplatform.wallet_tests.api.nats.NatsSubscriber;
import com.uplatform.wallet_tests.api.nats.NatsAttachmentHelper;
import com.uplatform.wallet_tests.api.nats.NatsConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NatsClient {

    private final NatsSubscriber subscriber;
    private final String streamPrefix;
    private final String natsBaseName;

    @Autowired
    public NatsClient(ObjectMapper objectMapper,
                      NatsAttachmentHelper attachmentHelper,
                      NatsConnectionManager connectionManager,
                      EnvironmentConfigurationProvider configProvider) {
        NatsConfig natsConfig = configProvider.getNatsConfig();

        this.streamPrefix = configProvider.getEnvironmentConfig().getNatsStreamPrefix();
        this.natsBaseName = natsConfig.getStreamName();
        String streamName = this.streamPrefix + this.natsBaseName;

        this.subscriber = new NatsSubscriber(
                connectionManager.getConnection(),
                connectionManager.getJetStream(),
                objectMapper,
                attachmentHelper,
                Duration.ofSeconds(natsConfig.getSearchTimeoutSeconds()),
                Duration.ofSeconds(natsConfig.getSubscriptionAckWaitSeconds()),
                Duration.ofSeconds(natsConfig.getSubscriptionInactiveThresholdSeconds()),
                streamName,
                natsConfig.getSubscriptionBufferSize(),
                natsConfig.getSubscriptionRetryCount(),
                natsConfig.getSubscriptionRetryDelayMs()
        );
    }

    public String buildWalletSubject(String playerUuid, String walletUuid) {
        String subjectBase = this.streamPrefix + "." + this.natsBaseName;
        String wildcard = "*";
        
        return String.format("%s.%s.%s.%s", subjectBase, wildcard, playerUuid, walletUuid);
    }

    public <T> CompletableFuture<NatsMessage<T>> findMessageAsync(String subject,
                                                                  Class<T> messageType,
                                                                  BiPredicate<T, String> filter) {
        return subscriber.findMessageAsync(subject, messageType, filter);
    }

}