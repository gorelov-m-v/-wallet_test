package com.uplatform.wallet_tests.api.redis.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.config.RedisAggregateConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.uplatform.wallet_tests.api.redis.client.CheckResult;

@Slf4j
@Component
public class RedisRetryHelper {

    private final ObjectMapper objectMapper;
    private final AllureAttachmentService attachmentService;
    private final int retryAttempts;
    private final long retryDelayMs;

    public RedisRetryHelper(ObjectMapper objectMapper,
                           AllureAttachmentService attachmentService,
                           EnvironmentConfigurationProvider configProvider) {
        this.objectMapper = objectMapper;
        this.attachmentService = attachmentService;

        RedisAggregateConfig aggregateConfig = Optional.ofNullable(configProvider)
                .map(EnvironmentConfigurationProvider::getEnvironmentConfig)
                .map(com.uplatform.wallet_tests.config.EnvironmentConfig::getRedis)
                .map(com.uplatform.wallet_tests.config.RedisConfig::getAggregate)
                .orElseThrow(() -> new IllegalStateException(
                        "RedisAggregateConfig not found in EnvironmentConfigurationProvider. Cannot initialize RedisRetryHelper."));

        this.retryAttempts = aggregateConfig.getRetryAttempts();
        this.retryDelayMs = aggregateConfig.getRetryDelayMs();

        log.info("RedisRetryHelper initialized with {} attempts and {}ms delay", retryAttempts, retryDelayMs);
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public <T> T deserializeValue(String rawValue, JavaType javaType) throws JsonProcessingException {
        return objectMapper.readValue(rawValue, javaType);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> waitForValue(
            String instance,
            String key,
            Type valueType,
            Object valueTypeInfo,
            BiFunction<String, String, Optional<String>> valueGetter,
            BiFunction<T, String, CheckResult> checkFunc) {

        String typeName = (valueTypeInfo instanceof Class<?> ? ((Class<?>) valueTypeInfo).getSimpleName() : valueTypeInfo.toString())
                .replace("com.fasterxml.jackson.core.type.TypeReference<", "")
                .replace(">", "");

        JavaType javaType = objectMapper.constructType(valueType);

        Optional<T> result = Optional.empty();
        String lastErrorMsg = "Initial state";
        String lastRawValue = null;
        T lastDeserializedValue = null;
        boolean interrupted = false;

        for (int i = 0; i < retryAttempts; i++) {
            final int attemptNum = i + 1;
            if (i > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[{}] Wait interrupted before attempt {} for key '{}'", instance, attemptNum, key);
                    lastErrorMsg = "Thread interrupted during wait";
                    interrupted = true;
                    break;
                }
            }

            try {
                Optional<String> rawValueOpt = valueGetter.apply(instance, key);
                if (rawValueOpt.isEmpty()) {
                    lastErrorMsg = "Key not found or value is empty";
                    log.warn("[{}] Attempt {}: Key '{}' not found or empty.", instance, attemptNum, key);
                    attachmentService.attachText(String.format("Attempt %d: Status", attemptNum), "Key not found or empty");
                } else {
                    String rawValue = rawValueOpt.get();
                    lastRawValue = rawValue;
                    try {
                        T deserializedValue = deserializeValue(rawValue, javaType);
                        lastDeserializedValue = deserializedValue;

                        if (checkFunc != null) {
                            CheckResult checkResult = runCheck(checkFunc, deserializedValue, rawValue);
                            if (!checkResult.isSuccess()) {
                                log.warn("[{}] Attempt {}: Check result: success=false, message='{}'", instance, attemptNum, checkResult.getMessage());
                            }
                            attachmentService.attachText(String.format("Attempt %d: Check Result", attemptNum), checkResult.getMessage());

                            if (checkResult.isSuccess()) {
                                result = Optional.of(deserializedValue);
                                attachValue("Redis Value Found & Validated (attempt " + attemptNum + ")", instance, key, deserializedValue, rawValue, checkResult.getMessage());
                                break;
                            } else {
                                lastErrorMsg = "Check failed: " + checkResult.getMessage();
                            }
                        } else {
                            result = Optional.of(deserializedValue);
                            attachValue("Redis Value Found (attempt " + attemptNum + ")", instance, key, deserializedValue, rawValue, "Check not required");
                            break;
                        }
                    } catch (JsonProcessingException e) {
                        lastErrorMsg = "Failed to deserialize JSON: " + e.getMessage();
                        log.error("[{}] Attempt {}: Failed to deserialize JSON to type {}. Error: {}", instance, attemptNum, typeName, e.getMessage());
                        attachmentService.attachText(String.format("Attempt %d: Deserialization Error", attemptNum), e.getMessage());
                        attachmentService.attachText(String.format("Attempt %d: Raw Value (Failed)", attemptNum), rawValue);
                    } catch (Exception e) {
                        lastErrorMsg = "Unexpected error during value processing: " + e.getMessage();
                        log.error("[{}] Attempt {}: Unexpected error processing key '{}'. Error: {}", instance, attemptNum, key, e.getMessage(), e);
                        attachmentService.attachText(String.format("Attempt %d: Unexpected Processing Error", attemptNum), e.getMessage());
                        interrupted = true;
                        break;
                    }
                }
            } catch (Exception e) {
                lastErrorMsg = "Unexpected error during attempt: " + e.getMessage();
                log.error("[{}] Unexpected error occurred during attempt {}: {}", instance, attemptNum, e.getMessage(), e);
                attachmentService.attachText(String.format("Attempt %d: Unexpected Error", attemptNum), e.getMessage());
                interrupted = true;
                break;
            }
        }

        if (result.isPresent()) {
            log.info("Successfully found value for key '{}' in Redis instance [{}]", key, instance);
        } else {
            log.error("Failed to find expected value for key '{}' in Redis instance [{}] after {} attempts. Last error: {}", key, instance, retryAttempts, lastErrorMsg);
            if (!interrupted) {
                attachmentService.attachText("Final State (Failure)", createAttachmentContent(instance, key, lastDeserializedValue, lastRawValue,
                        "Failure after all attempts. Last error: " + lastErrorMsg));
            }
        }
        return result;
    }

    private <T> CheckResult runCheck(BiFunction<T, String, CheckResult> checkFunc, T value, String rawValue) {
        return checkFunc.apply(value, rawValue);
    }

    private <T> void attachValue(String title, String instance, String key, T value, String rawValue, String status) {
        attachmentService.attachText(title, createAttachmentContent(instance, key, value, rawValue, status));
    }

    public <T> String createAttachmentContent(String instance, String key, T deserializedValue, String rawValue, String statusMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Redis Instance: ").append(instance).append("\n");
        sb.append("Key: ").append(key).append("\n");
        sb.append("Status: ").append(statusMessage).append("\n\n");

        if (deserializedValue != null) {
            sb.append("Deserialized Type: ").append(deserializedValue.getClass().getName()).append("\n");
            try {
                sb.append("Deserialized Value (JSON):\n");
                sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedValue));
            } catch (JsonProcessingException e) {
                sb.append("Could not format deserialized value as pretty JSON: ").append(e.getMessage()).append("\n");
                sb.append("Deserialized Value (toString()):\n").append(deserializedValue);
            }
        } else if (rawValue != null && !rawValue.isEmpty()) {
            sb.append("Raw Value:\n").append(rawValue);
        } else {
            sb.append("No value retrieved or value was empty.");
        }
        return sb.toString();
    }
}
