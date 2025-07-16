package com.uplatform.wallet_tests.tests.util.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.tests.util.comparator.ComparatorStrategyManager;
import com.uplatform.wallet_tests.tests.util.comparator.PayloadComparatorStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class KafkaNatsComparator {

    private final ObjectMapper objectMapper;
    private final ComparatorStrategyManager strategyManager;

    public boolean areEquivalent(WalletProjectionMessage kafkaMessage, NatsMessage<?> natsMessage) {
        if (kafkaMessage == null || natsMessage == null) {
            return false;
        }

        long kafkaSeq = kafkaMessage.getSeqNumber();
        long natsSeq = natsMessage.getSequence();
        if (kafkaSeq != natsSeq) {
            return false;
        }

        String kafkaType = kafkaMessage.getType();
        String natsType = natsMessage.getType();
        if (!Objects.equals(kafkaType, natsType)) {
            return false;
        }
        if (kafkaType == null || kafkaType.trim().isEmpty()) {
            return false;
        }

        Optional<PayloadComparatorStrategy> strategyOpt = strategyManager.findStrategy(kafkaType);
        if (strategyOpt.isEmpty()) {
            return false;
        }
        PayloadComparatorStrategy strategy = strategyOpt.get();

        NatsSubjectInfo natsSubjectInfo = extractInfoFromSubject(natsMessage.getSubject());
        if (natsSubjectInfo == null) {
            return false;
        }
        if (!Objects.equals(kafkaMessage.getPlayerUuid(), natsSubjectInfo.playerUuid())) {
            return false;
        }
        if (!Objects.equals(kafkaMessage.getWalletUuid(), natsSubjectInfo.walletUuid())) {
            return false;
        }

        String kafkaPayloadString = kafkaMessage.getPayload();
        if (kafkaPayloadString == null || kafkaPayloadString.trim().isEmpty()) {
            return false;
        }

        return strategy.deserializeAndCompare(
                kafkaPayloadString,
                natsMessage.getPayload(),
                kafkaSeq,
                objectMapper,
                kafkaType
        );
    }

    private NatsSubjectInfo extractInfoFromSubject(String subject) {
        if (subject == null || subject.isEmpty()) {
            return null;
        }
        String[] parts = subject.split("\\.");
        if (parts.length >= 2) {
            String potentialPlayerUuid = parts[parts.length - 2];
            String potentialWalletUuid = parts[parts.length - 1];
            if (isValidUuidFormat(potentialPlayerUuid) && isValidUuidFormat(potentialWalletUuid)) {
                return new NatsSubjectInfo(potentialPlayerUuid, potentialWalletUuid);
            } else {

                return null;
            }
        }
        return null;
    }

    private boolean isValidUuidFormat(String uuidString) {
        if (uuidString == null || uuidString.length() != 36) {
            return false;
        }
        return uuidString.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
    private record NatsSubjectInfo(String playerUuid, String walletUuid) {}
}