package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsPreventGambleSettedPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class PreventGambleSettedPayloadComparator implements PayloadComparatorStrategy {

    private static final String EVENT_TYPE = "setting_prevent_gamble_setted";
    private static final Set<String> SUPPORTED_TYPES = Set.of(EVENT_TYPE);

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsPreventGambleSettedPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload,
                                 Object natsPayload,
                                 long seqNum,
                                 String actualEventType) {
        var kafka = (NatsPreventGambleSettedPayload) deserializedKafkaPayload;
        var nats  = (NatsPreventGambleSettedPayload) natsPayload;
        boolean ok = true;

        if (kafka.isGamblingActive() != nats.isGamblingActive()) {
            logMismatch(seqNum,
                    "isGamblingActive",
                    kafka.isGamblingActive(),
                    nats.isGamblingActive(),
                    actualEventType);
            ok = false;
        }
        if (kafka.isBettingActive() != nats.isBettingActive()) {
            logMismatch(seqNum,
                    "isBettingActive",
                    kafka.isBettingActive(),
                    nats.isBettingActive(),
                    actualEventType);
            ok = false;
        }
        if (kafka.getCreatedAt() != nats.getCreatedAt()) {
            logMismatch(seqNum,
                    "createdAt",
                    kafka.getCreatedAt(),
                    nats.getCreatedAt(),
                    actualEventType);
            ok = false;
        }

        return ok;
    }
}
