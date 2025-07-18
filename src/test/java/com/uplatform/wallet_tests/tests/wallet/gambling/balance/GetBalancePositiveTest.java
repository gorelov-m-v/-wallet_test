package com.uplatform.wallet_tests.tests.wallet.gambling.balance;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/balance")
@Suite("Позитивный сценарий получения баланса игрока в игровой сессии")
@Tag("Gambling") @Tag("Wallet")
class GetBalancePositiveTest extends BaseTest {
    @Autowired private ManagerClient managerClient;

    @Test
    @DisplayName("Позитивный сценарий получения баланса игрока в игровой сессии")
    void shouldRegisterAdjustCreateSessionAndGetBalance() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BigDecimal initialAdjustmentAmount;
        }
        final TestData testData = new TestData();

        testData.initialAdjustmentAmount = new BigDecimal("250.50");

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(testData.initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Запрос баланса пользователя через /balance", () -> {
            var sessionToken = testData.gameLaunchData.getDbGameSession().getGameSessionUuid();
            var queryString = "sessionToken=" + sessionToken;

            var response = managerClient.getBalance(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BALANCE, queryString, null),
                    testData.gameLaunchData.getDbGameSession().getGameSessionUuid()
            );

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.balance.status_code"),
                    () -> assertEquals(0, testData.initialAdjustmentAmount.compareTo(response.getBody().getBalance()), "manager_api.balance.balance")
            );
        });
    }
}