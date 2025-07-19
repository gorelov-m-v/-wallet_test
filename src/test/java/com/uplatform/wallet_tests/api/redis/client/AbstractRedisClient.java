package com.uplatform.wallet_tests.api.redis.client;

import com.uplatform.wallet_tests.api.redis.exception.RedisClientException;
import com.uplatform.wallet_tests.api.redis.model.WalletFilterCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.BiFunction;

import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.fasterxml.jackson.core.type.TypeReference;

@Slf4j

public abstract class AbstractRedisClient<T> {

    protected final String instanceName;
    protected final RedisTemplate<String, String> redisTemplate;
    protected final RedisRetryHelper retryHelper;
    protected final AllureAttachmentService attachmentService;
    private final TypeReference<T> typeReference;

    protected AbstractRedisClient(String instanceName,
                                  RedisTemplate<String, String> redisTemplate,
                                  RedisRetryHelper retryHelper,
                                  AllureAttachmentService attachmentService,
                                  TypeReference<T> typeReference) {
        this.instanceName = instanceName;
        this.redisTemplate = redisTemplate;
        this.retryHelper = retryHelper;
        this.attachmentService = attachmentService;
        this.typeReference = typeReference;
        log.info("RedisClient initialized for instance: {}", this.instanceName);
        checkConnection();
    }

    private void checkConnection() {
        if (redisTemplate.getConnectionFactory() != null) {
            try (RedisConnection conn = redisTemplate.getConnectionFactory().getConnection()) {
                String pong = conn.ping();
                log.info("Redis connection check successful for instance [{}]: PING -> {}", instanceName, pong);
            } catch (Exception e) {
                log.error("!!! Failed to connect or ping Redis instance [{}] on startup: {}", instanceName, e.getMessage());
            }
        } else {
            log.error("!!! Redis ConnectionFactory is NULL for instance [{}]", instanceName);
        }
    }

    private Optional<String> getValue(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value).filter(s -> !s.isEmpty());
        } catch (RedisSystemException e) {
            log.error("[{}] Redis connection/system error when getting key '{}': {}", instanceName, key, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[{}] Unexpected error getting value from Redis for key '{}': {}", instanceName, key, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public T getWithRetry(String key) {
        if (key == null) {
            String errorMsg = String.format("[%s] Cannot get value: key is null.", instanceName);
            log.error(errorMsg);
            attachmentService.attachText("Redis Error", errorMsg);
            throw new RedisClientException(errorMsg);
        }
        Optional<T> result = retryHelper.waitForValue(
                instanceName,
                key,
                typeReference.getType(),
                typeReference,
                (inst, k) -> getValue(k),
                null);
        return result.orElseThrow(() -> new RedisClientException(String.format(
                "[%s] Failed to get value for key '%s' after %d attempts",
                instanceName,
                key,
                retryHelper.getRetryAttempts())));
    }

    public T getWithCheck(String key, BiFunction<T, String, CheckResult> checkFunc) {
        if (key == null) {
            String errorMsg = String.format("[%s] Cannot check value: key is null.", instanceName);
            log.error(errorMsg);
            attachmentService.attachText("Redis Error", errorMsg);
            throw new RedisClientException(errorMsg);
        }
        Optional<T> result = retryHelper.waitForValue(
                instanceName,
                key,
                typeReference.getType(),
                typeReference,
                (inst, k) -> getValue(k),
                checkFunc);
        return result.orElseThrow(() -> new RedisClientException(String.format(
                "[%s] Failed to get expected value for key '%s' after %d attempts",
                instanceName,
                key,
                retryHelper.getRetryAttempts())));
    }

    public String describeCriteria(WalletFilterCriteria criteria) {
        if (criteria == null) {
            return "[null criteria]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        criteria.getCurrency().ifPresent(c -> joiner.add("currency=" + c));
        criteria.getType().ifPresent(t -> joiner.add("type=" + t));
        criteria.getStatus().ifPresent(s -> joiner.add("status=" + s));
        return joiner.length() <= 2 ? "[no specific criteria]" : joiner.toString();
    }
}
