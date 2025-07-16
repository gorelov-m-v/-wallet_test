package com.uplatform.wallet_tests.tests.util.comparator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.Set;

public interface PayloadComparatorStrategy {

    Logger log = LoggerFactory.getLogger(PayloadComparatorStrategy.class);

    Set<String> getSupportedEventTypes();

    Class<?> getPayloadClass();

    boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType);

    default boolean deserializeAndCompare(String kafkaPayloadJson, Object natsPayload, long seqNum, ObjectMapper objectMapper, String actualEventType) {
        Object deserializedKafkaPayload;
        Class<?> expectedPayloadClass = getPayloadClass();

        try {
            deserializedKafkaPayload = objectMapper.readValue(kafkaPayloadJson, expectedPayloadClass);
        } catch (JsonProcessingException e) {
            return false;
        }

        if (natsPayload == null) {
            return false;
        }
        if (!expectedPayloadClass.isInstance(natsPayload)) {
            return false;
        }

        try {
            return compareAndLog(deserializedKafkaPayload, natsPayload, seqNum, actualEventType);
        } catch (Exception e) {
            return false;
        }
    }

    default void logMismatch(long seqNum, String fieldName, Object kafkaVal, Object natsVal, String actualEventType) {
        log.debug("Payload mismatch (SeqNum: {}, Type: {}): Field '{}' differs. Kafka_Payload='{}', Nats_Payload='{}'.",
                seqNum, actualEventType, fieldName, kafkaVal, natsVal);
    }

    default void checkAndLogMismatch(long seqNum, String fieldName, Object val1, Object val2, String actualEventType) {
        if (!java.util.Objects.equals(val1, val2)) {
            logMismatch(seqNum, fieldName, val1, val2, actualEventType);
        }
    }

    static int compareBigDecimals(BigDecimal bd1, BigDecimal bd2) {
        if (bd1 == null && bd2 == null) {
            return 0;
        }
        if (bd1 == null) {
            return -1;
        }
        if (bd2 == null) {
            return 1;
        }
        return bd1.compareTo(bd2);
    }
}