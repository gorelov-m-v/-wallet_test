package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class GamblingEventPayloadComparator implements PayloadComparatorStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "won_from_gamble",
            "betted_from_gamble",
            "refunded_from_gamble",
            "rollbacked_from_gamble",
            "tournament_won_from_gamble"
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsGamblingEventPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsGamblingEventPayload kafka = (NatsGamblingEventPayload) deserializedKafkaPayload;
        NatsGamblingEventPayload nats = (NatsGamblingEventPayload) natsPayload;

        if (!Objects.equals(kafka, nats)) {
            logDetailedDifference(kafka, nats, seqNum, actualEventType);
            return false;
        }
        return true;
    }

    private void logDetailedDifference(NatsGamblingEventPayload kafka, NatsGamblingEventPayload nats, long seqNum, String actualEventType) {
        checkAndLogMismatch(seqNum, "Payload Transaction UUID", kafka.getUuid(), nats.getUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Bet UUID", kafka.getBetUuid(), nats.getBetUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Game Session UUID", kafka.getGameSessionUuid(), nats.getGameSessionUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Provider Round ID", kafka.getProviderRoundId(), nats.getProviderRoundId(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Currency", kafka.getCurrency(), nats.getCurrency(), actualEventType);

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getAmount(), nats.getAmount()) != 0) {
            logMismatch(seqNum, "Payload Amount (value)", kafka.getAmount(), nats.getAmount(), actualEventType);
        } else if (!Objects.equals(kafka.getAmount(), nats.getAmount())) {
            logMismatch(seqNum, "Payload Amount (scale differs)", kafka.getAmount(), nats.getAmount(), actualEventType);
        }

        checkAndLogMismatch(seqNum, "Payload Type (internal)", kafka.getType(), nats.getType(), actualEventType);
        if (kafka.isProviderRoundClosed() != nats.isProviderRoundClosed()) {
            logMismatch(seqNum, "Payload Provider Round Closed", kafka.isProviderRoundClosed(), nats.isProviderRoundClosed(), actualEventType);
        }
        checkAndLogMismatch(seqNum, "Payload Message", kafka.getMessage(), nats.getMessage(), actualEventType);
        if (kafka.getCreatedAt() != nats.getCreatedAt()) {
            logMismatch(seqNum, "Payload CreatedAt Timestamp", kafka.getCreatedAt(), nats.getCreatedAt(), actualEventType);
        }
        checkAndLogMismatch(seqNum, "Payload Direction", kafka.getDirection(), nats.getDirection(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Operation", kafka.getOperation(), nats.getOperation(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Node UUID", kafka.getNodeUuid(), nats.getNodeUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Game UUID", kafka.getGameUuid(), nats.getGameUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Provider UUID", kafka.getProviderUuid(), nats.getProviderUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Wagered Deposit Info", kafka.getWageredDepositInfo(), nats.getWageredDepositInfo(), actualEventType);
        checkAndLogMismatch(seqNum, "Payload Currency Conversion Info", kafka.getCurrencyConversionInfo(), nats.getCurrencyConversionInfo(), actualEventType);
    }
}