package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsBalanceAdjustedPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class BalanceAdjustedPayloadComparator implements PayloadComparatorStrategy {

    private static final String EVENT_TYPE = "balance_adjusted";
    private static final Set<String> SUPPORTED_TYPES = Set.of(EVENT_TYPE);

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsBalanceAdjustedPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsBalanceAdjustedPayload kafka = (NatsBalanceAdjustedPayload) deserializedKafkaPayload;
        NatsBalanceAdjustedPayload nats = (NatsBalanceAdjustedPayload) natsPayload;

        if (!Objects.equals(kafka, nats)) {
            log.debug("Comparison failed (SeqNum: {}): Payload objects are not equal using DTO.equals() for type '{}'.",
                    seqNum, actualEventType);
            logDetailedDifference(kafka, nats, seqNum, actualEventType);
            return false;
        }
        return true;
    }

    private void logDetailedDifference(NatsBalanceAdjustedPayload kafka, NatsBalanceAdjustedPayload nats, long seqNum, String actualEventType) {
        log.debug("Detailed payload comparison for {} (SeqNum: {})", actualEventType, seqNum);

        checkAndLogMismatch(seqNum, "UUID", kafka.getUuid(), nats.getUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Currency", kafka.getCurrency(), nats.getCurrency(), actualEventType);

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getAmount(), nats.getAmount()) != 0) {
            logMismatch(seqNum, "Amount (value)", kafka.getAmount(), nats.getAmount(), actualEventType);
        } else if (!Objects.equals(kafka.getAmount(), nats.getAmount())) {
            log.trace("Amount values equal via compareTo but differ in scale (SeqNum: {}). Kafka: {}, Nats: {}",
                    seqNum, kafka.getAmount(), nats.getAmount());
            logMismatch(seqNum, "Amount (scale differs)", kafka.getAmount(), nats.getAmount(), actualEventType);
        }

        if (kafka.getOperationType() != nats.getOperationType()) {
            logMismatch(seqNum, "Operation Type", kafka.getOperationType(), nats.getOperationType(), actualEventType);
        }
        if (kafka.getDirection() != nats.getDirection()) {
            logMismatch(seqNum, "Direction", kafka.getDirection(), nats.getDirection(), actualEventType);
        }
        if (kafka.getReason() != nats.getReason()) {
            logMismatch(seqNum, "Reason", kafka.getReason(), nats.getReason(), actualEventType);
        }
        checkAndLogMismatch(seqNum, "Comment", kafka.getComment(), nats.getComment(), actualEventType);
        checkAndLogMismatch(seqNum, "User UUID", kafka.getUserUuid(), nats.getUserUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "User Name", kafka.getUserName(), nats.getUserName(), actualEventType);
    }
}