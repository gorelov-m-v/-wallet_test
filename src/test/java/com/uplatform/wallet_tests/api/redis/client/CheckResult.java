package com.uplatform.wallet_tests.api.redis.client;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckResult {
    private boolean success;
    private String message;
}
