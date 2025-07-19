package com.uplatform.wallet_tests.api.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
public class MessageFinder {

    private final ObjectMapper objectMapper;
    private final KafkaAllureReporter allureReporter;
    private final ConcurrentMap<String, JsonPath> pathCache = new ConcurrentHashMap<>();

    @Autowired
    public MessageFinder(ObjectMapper objectMapper, KafkaAllureReporter allureReporter) {
        this.objectMapper = objectMapper;
        this.allureReporter = allureReporter;
    }

    public <T> Optional<T> searchAndDeserialize(
            ConcurrentLinkedDeque<ConsumerRecord<String, String>> buffer,
            Map<String, String> filterCriteria,
            Class<T> targetClass,
            String topicName
    ) {
        if (buffer == null || buffer.isEmpty()) {
            return Optional.empty();
        }

        Iterator<ConsumerRecord<String, String>> descendingIterator = buffer.descendingIterator();
        while (descendingIterator.hasNext()) {
            ConsumerRecord<String, String> record = descendingIterator.next();

            if (matchesFilter(record.value(), filterCriteria)) {
                Optional<T> deserialized = tryDeserialize(record, targetClass);
                if (deserialized.isPresent()) {
                    allureReporter.addFoundMessageAttachment(record);
                    return deserialized;
                }
            }
        }
        return Optional.empty();
    }

    public int countMatchingMessages(
            ConcurrentLinkedDeque<ConsumerRecord<String, String>> buffer,
            Map<String, String> filterCriteria
    ) {
        if (buffer == null || buffer.isEmpty()) {
            return 0;
        }

        int count = 0;
        Iterator<ConsumerRecord<String, String>> iterator = buffer.iterator();
        while (iterator.hasNext()) {
            ConsumerRecord<String, String> record = iterator.next();
            if (matchesFilter(record.value(), filterCriteria)) {
                count++;
            }
        }
        return count;
    }

    private <T> Optional<T> tryDeserialize(ConsumerRecord<String, String> record, Class<T> targetClass) {
        if (record == null || record.value() == null) {
            return Optional.empty();
        }
        String jsonValue = record.value();
        try {
            T value = objectMapper.readValue(jsonValue, targetClass);
            return Optional.of(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize Kafka message (Offset: {}, Topic: {}) into {}: {}. Value snippet: '{}...'",
                    record.offset(), record.topic(), targetClass.getSimpleName(), e.getMessage(),
                    jsonValue.substring(0, Math.min(jsonValue.length(), 100)));
            allureReporter.addDeserializationErrorAttachment(record, targetClass, e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error during deserialization attempt for Kafka message (Offset: {}, Topic: {}) into {}: {}",
                    record.offset(), record.topic(), targetClass.getSimpleName(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private boolean matchesFilter(String jsonValue, Map<String, String> filterCriteria) {
        if (jsonValue == null) {
            return filterCriteria.isEmpty();
        }
        if (filterCriteria.isEmpty()) {
            return true;
        }

        ReadContext ctx;
        try {
            ctx = JsonPath.parse(jsonValue);
        } catch (Exception e) {
            log.warn("Failed to parse JSON for filter check: {}", e.getMessage());
            return false;
        }

        for (Map.Entry<String, String> entry : filterCriteria.entrySet()) {
            String rawPath = entry.getKey();
            String normalizedPath = rawPath.startsWith("$") ? rawPath : "$." + rawPath;
            JsonPath compiledPath = pathCache.computeIfAbsent(normalizedPath, JsonPath::compile);
            Object actual;
            try {
                actual = ctx.read(compiledPath);
            } catch (Exception e) {
                return false;
            }
            String actualString = actual == null ? null : String.valueOf(actual);
            if (!Objects.equals(actualString, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

}
