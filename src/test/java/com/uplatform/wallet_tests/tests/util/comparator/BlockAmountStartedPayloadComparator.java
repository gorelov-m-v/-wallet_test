package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsBlockAmountEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class BlockAmountStartedPayloadComparator implements PayloadComparatorStrategy {
    
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            NatsEventType.BLOCK_AMOUNT_STARTED.getHeaderValue()
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsBlockAmountEventPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsBlockAmountEventPayload kafka = (NatsBlockAmountEventPayload) deserializedKafkaPayload;
        NatsBlockAmountEventPayload nats = (NatsBlockAmountEventPayload) natsPayload;

        if (!Objects.equals(kafka, nats)) {
            logDetailedDifference(kafka, nats, seqNum, actualEventType);
            return false;
        }
        return true;
    }

    private void logDetailedDifference(NatsBlockAmountEventPayload kafka, NatsBlockAmountEventPayload nats, long seqNum, String actualEventType) {
        checkAndLogMismatch(seqNum, "UUID", kafka.getUuid(), nats.getUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "Status", kafka.getStatus(), nats.getStatus(), actualEventType);
        
        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getAmount(), nats.getAmount()) != 0) {
            logMismatch(seqNum, "Amount (value)", kafka.getAmount(), nats.getAmount(), actualEventType);
        } else if (!Objects.equals(kafka.getAmount(), nats.getAmount())) {
            logMismatch(seqNum, "Amount (scale differs)", kafka.getAmount(), nats.getAmount(), actualEventType);
        }
        
        checkAndLogMismatch(seqNum, "Reason", kafka.getReason(), nats.getReason(), actualEventType);
        checkAndLogMismatch(seqNum, "Type", kafka.getType(), nats.getType(), actualEventType);
        checkAndLogMismatch(seqNum, "User UUID", kafka.getUserUuid(), nats.getUserUuid(), actualEventType);
        checkAndLogMismatch(seqNum, "User Name", kafka.getUserName(), nats.getUserName(), actualEventType);
        
        if (kafka.getCreatedAt() != nats.getCreatedAt()) {
            logMismatch(seqNum, "Created At", kafka.getCreatedAt(), nats.getCreatedAt(), actualEventType);
        }
        
        if (kafka.getExpiredAt() != nats.getExpiredAt()) {
            logMismatch(seqNum, "Expired At", kafka.getExpiredAt(), nats.getExpiredAt(), actualEventType);
        }
    }
}
