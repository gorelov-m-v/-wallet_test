package com.uplatform.wallet_tests.tests.default_steps.dto;

import com.uplatform.wallet_tests.api.db.entity.wallet.WalletGameSession;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameResponseBody;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.http.ResponseEntity;

@Getter
@ToString
@RequiredArgsConstructor
public class GameLaunchData {
    private final WalletGameSession dbGameSession;
    private final ResponseEntity<LaunchGameResponseBody> launchGameResponse;
}