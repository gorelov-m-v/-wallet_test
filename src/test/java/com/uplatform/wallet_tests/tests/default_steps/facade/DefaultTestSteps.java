package com.uplatform.wallet_tests.tests.default_steps.facade;

import com.uplatform.wallet_tests.tests.default_steps.steps.CreateGameSessionStep;
import com.uplatform.wallet_tests.tests.default_steps.steps.PlayerRegistrationStep;
import com.uplatform.wallet_tests.tests.default_steps.steps.PlayerFullRegistrationStep;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DefaultTestSteps {
    private final PlayerRegistrationStep playerRegistrationStep;
    private final PlayerFullRegistrationStep playerFullRegistrationStep;
    private final CreateGameSessionStep createGameSessionStep;

    public RegisteredPlayerData registerNewPlayer(BigDecimal adjustmentAmount) {
        return playerRegistrationStep.registerNewPlayer(adjustmentAmount);
    }

    public RegisteredPlayerData registerNewPlayer() {
        return playerRegistrationStep.registerNewPlayer(BigDecimal.ZERO);
    }

    public RegisteredPlayerData registerNewPlayerWithKyc() {
        return playerFullRegistrationStep.registerNewPlayerWithKyc();
    }

    public GameLaunchData createGameSession(RegisteredPlayerData playerData) {
        return createGameSessionStep.createGameSession(playerData);
    }
}