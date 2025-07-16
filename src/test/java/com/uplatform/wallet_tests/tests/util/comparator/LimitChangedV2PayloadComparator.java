package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LimitChangedV2PayloadComparator implements PayloadComparatorStrategy {

    private static final String EVENT_TYPE  = "limit_changed_v2";
    private static final Set<String> SUPPORTED_TYPES = Set.of(EVENT_TYPE);

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsLimitChangedV2Payload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload,
                                 Object natsPayload,
                                 long seqNum,
                                 String actualEventType) {

        NatsLimitChangedV2Payload kafka = (NatsLimitChangedV2Payload) deserializedKafkaPayload;
        NatsLimitChangedV2Payload nats  = (NatsLimitChangedV2Payload) natsPayload;

        if (!Objects.equals(kafka.getEventType(), nats.getEventType())) {
            logMismatch(seqNum, "event_type",
                    kafka.getEventType(), nats.getEventType(), actualEventType);
            return false;
        }

        return limitsEqual(kafka.getLimits(), nats.getLimits(), seqNum, actualEventType);
    }

    private boolean limitsEqual(List<NatsLimitChangedV2Payload.LimitDetail> kLimits,
                                List<NatsLimitChangedV2Payload.LimitDetail> nLimits,
                                long seqNum,
                                String eventType) {

        if (kLimits == null || nLimits == null) {
            logMismatch(seqNum, "limits", kLimits, nLimits, eventType);
            return false;
        }
        if (kLimits.size() != nLimits.size()) {
            logMismatch(seqNum, "limits.size", kLimits.size(), nLimits.size(), eventType);
            return false;
        }

        Map<String, NatsLimitChangedV2Payload.LimitDetail> kafkaById =
                kLimits.stream()
                        .collect(Collectors.toMap(NatsLimitChangedV2Payload.LimitDetail::getExternalId, d -> d));

        boolean ok = true;
        for (NatsLimitChangedV2Payload.LimitDetail natsDetail : nLimits) {
            NatsLimitChangedV2Payload.LimitDetail kafkaDetail = kafkaById.get(natsDetail.getExternalId());
            if (kafkaDetail == null) {
                logMismatch(seqNum, "limits.external_id(not found)",
                        null, natsDetail.getExternalId(), eventType);
                ok = false;
                continue;
            }
            ok &= compareLimit(seqNum, kafkaDetail, natsDetail, eventType);
        }
        return ok;
    }

    private boolean compareLimit(long seqNum,
                                 NatsLimitChangedV2Payload.LimitDetail k,
                                 NatsLimitChangedV2Payload.LimitDetail n,
                                 String eventType) {

        boolean matched = true;

        matched &= objectsEqual(seqNum, "limit_type",
                k.getLimitType(), n.getLimitType(), eventType);
        matched &= objectsEqual(seqNum, "interval_type",
                k.getIntervalType(), n.getIntervalType(), eventType);

        BigDecimal kAmt = k.getAmount();
        BigDecimal nAmt = n.getAmount();
        if (PayloadComparatorStrategy.compareBigDecimals(kAmt, nAmt) != 0) {
            logMismatch(seqNum, "amount(value)", kAmt, nAmt, eventType);
            matched = false;
        } else if (!Objects.equals(kAmt, nAmt)) {
            logMismatch(seqNum, "amount(scale)", kAmt, nAmt, eventType);
        }

        matched &= objectsEqual(seqNum, "currency_code",
                k.getCurrencyCode(), n.getCurrencyCode(), eventType);
        matched &= objectsEqual(seqNum, "started_at",
                k.getStartedAt(), n.getStartedAt(), eventType);
        matched &= objectsEqual(seqNum, "expires_at",
                k.getExpiresAt(), n.getExpiresAt(), eventType);
        matched &= objectsEqual(seqNum, "status",
                k.getStatus(), n.getStatus(), eventType);

        return matched;
    }

    private boolean objectsEqual(long seqNum,
                                 String field,
                                 Object kafkaVal,
                                 Object natsVal,
                                 String eventType) {
        if (!Objects.equals(kafkaVal, natsVal)) {
            logMismatch(seqNum, field, kafkaVal, natsVal, eventType);
            return false;
        }
        return true;
    }
}
