package com.uplatform.wallet_tests.api.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAllureReporter {

    private final ObjectMapper objectMapper;
    private final AllureAttachmentService attachmentService;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public void addSearchInfoAttachment(
            String fullTopicName,
            String searchOrigin,
            Class<?> targetClass,
            Map<String, String> filterCriteria) {
        String filterCriteriaString = filterCriteria.entrySet().stream()
                .map(e -> "- " + e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining("\n"));

        if (filterCriteriaString.isEmpty()) filterCriteriaString = "(No specific filter criteria)";

        String searchInfoContent = String.format(
                "Topic %s: %s\nTarget Type: %s\nCondition:\n%s",
                searchOrigin,
                fullTopicName,
                targetClass.getSimpleName(),
                filterCriteriaString
        );
        attachmentService.attachText("Search Info", searchInfoContent);
    }

    public void addFoundMessageAttachment(ConsumerRecord<String, String> record) {
        String timestampStr = "N/A";
        long timestampEpoch = record.timestamp();
        if (timestampEpoch > 0 && record.timestampType() != TimestampType.NO_TIMESTAMP_TYPE) {
            try {
                timestampStr = Instant.ofEpochMilli(timestampEpoch)
                        .atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
            } catch (Exception timeEx) {
                log.trace("Error formatting timestamp {}", timestampEpoch, timeEx);
            }
        }
        String rawValue = record.value() != null ? record.value() : "(null value)";
        String formattedPayload = rawValue;
        if (record.value() != null) {
            try {
                Object jsonObject = objectMapper.readValue(rawValue, Object.class);
                formattedPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            } catch (Exception formatEx) {
                log.trace("Could not pretty-print payload for attachment: {}", formatEx.getMessage());
            }
        }
        String attachmentContent = String.format(
                "Partition: %d\nOffset: %d\nTimestamp: %s\n---\nPayload:\n---\n%s",
                record.partition(),
                record.offset(),
                timestampStr,
                formattedPayload
        );
        attachmentService.attachText("Found Kafka Message", attachmentContent);
    }

    public <T> void addMessagesNotFoundAttachment(
            String fullTopicName,
            Map<String, String> filterCriteria,
            Class<T> targetClass,
            String searchOrigin) {
        String filterCriteriaString = filterCriteria.entrySet().stream()
                .map(e -> "- " + e.getKey() + " = " + e.getValue())
                .collect(Collectors.joining("\n"));

        if (filterCriteriaString.isEmpty()) filterCriteriaString = "(No specific filter criteria)";
        String targetClassName = (targetClass != null) ? targetClass.getName() : "N/A";

        String content = String.format(
                "Kafka Message Not Found\n===\nTopic Searched %s: %s\nTarget Type: %s\nFilter Criteria:\n%s",
                searchOrigin,
                fullTopicName,
                targetClassName,
                filterCriteriaString
        );
        attachmentService.attachText("Kafka Message Not Found", content);
    }

    public <T> void addDeserializationErrorAttachment(ConsumerRecord<String, String> record, Class<T> targetClass, JsonProcessingException e) {
        String errorTimestampStr = "N/A";
        long errorTimestampEpoch = record.timestamp();
        if (errorTimestampEpoch > 0 && record.timestampType() != TimestampType.NO_TIMESTAMP_TYPE) {
            try {
                errorTimestampStr = Instant.ofEpochMilli(errorTimestampEpoch)
                        .atZone(ZoneId.systemDefault())
                        .format(TIMESTAMP_FORMATTER);
            } catch (Exception ignored) {
            }
        }
        String originalPayload = record.value() != null ? record.value() : "(null)";
        if (record.value() != null) {
            try {
                Object jsonObject = objectMapper.readValue(originalPayload, Object.class);
                originalPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            } catch (Exception ignored) {
            }
        }
        String errorAttachmentContent = String.format("Kafka Deserialization Error\n===\nFailed to deserialize into: %s\n\nMessage Metadata:\n---\nTopic: %s\nOffset: %d\nPartition: %d\nTimestamp: %s\n\nError Details:\n---\n%s\n\nOriginal Payload:\n---\n%s",
                targetClass.getName(),
                record.topic(),
                record.offset(),
                record.partition(),
                errorTimestampStr,
                e.getMessage(),
                originalPayload);

        attachmentService.attachText(
                String.format("Deserialization Error (Offset %d)", record.offset()),
                errorAttachmentContent);
    }
}