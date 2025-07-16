package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.BlockAmountRevokedEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class BlockAmountRevokedPayloadComparator implements PayloadComparatorStrategy {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            NatsEventType.BLOCK_AMOUNT_REVOKED.getHeaderValue()
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return BlockAmountRevokedEventPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        BlockAmountRevokedEventPayload kafka = (BlockAmountRevokedEventPayload) deserializedKafkaPayload;
        BlockAmountRevokedEventPayload nats = (BlockAmountRevokedEventPayload) natsPayload;

        boolean uuidsMatch = Objects.equals(kafka.getUuid(), nats.getUuid());

        if (!uuidsMatch) {
            logMismatch(seqNum, "UUID", kafka.getUuid(), nats.getUuid(), actualEventType);
            return false;
        }
        return true;
    }
}