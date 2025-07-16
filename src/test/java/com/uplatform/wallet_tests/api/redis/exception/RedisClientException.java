package com.uplatform.wallet_tests.api.redis.exception;

public class RedisClientException extends RuntimeException {
    public RedisClientException(String message) {
        super(message);
    }
}