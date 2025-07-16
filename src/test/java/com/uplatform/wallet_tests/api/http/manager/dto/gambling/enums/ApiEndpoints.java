package com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiEndpoints {
    BALANCE("/balance"),
    BET("/bet"),
    WIN("/win"),
    REFUND("/refund"),
    ROLLBACK("/rollback"),
    TOURNAMENT("/tournament"),
    UNKNOWN("/unknown");
    
    private final String path;
}
