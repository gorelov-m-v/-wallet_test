package com.uplatform.wallet_tests.tests.default_steps.dto;

import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckResponse;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.http.ResponseEntity;

@Getter
@ToString
@RequiredArgsConstructor
public class RegisteredPlayerData {
    private final ResponseEntity<TokenCheckResponse> authorizationResponse;
    private final WalletFullData walletData;
}