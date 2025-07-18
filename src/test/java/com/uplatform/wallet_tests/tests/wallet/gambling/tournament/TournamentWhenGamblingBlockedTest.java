package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
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
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Позитивные сценарии: /tournament")
@Tag("Gambling") @Tag("Wallet")
class TournamentWhenGamblingBlockedTest extends BaseTest {
    @Autowired private CapAdminClient capAdminClient;

    @Test
    @DisplayName("Получение выигрыша в турнире игроком с заблокированным гемблингом")
    void shouldAwardTournamentWinAndVerify() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BigDecimal adjustmentAmount;
            BigDecimal tournamentAmount;
        }
        final TestData testData = new TestData();

        testData.adjustmentAmount = new BigDecimal("150.00");
        testData.tournamentAmount = new BigDecimal("50.25");

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(testData.adjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии и проверка в БД", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.create_game_session");
        });

        step("CAP API: Блокировка гемблинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(false)
                    .bettingEnabled(true)
                    .build();

            var response = capAdminClient.updateBlockers(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap.update_blockers.status_code");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            var request = TournamentRequestBody.builder()
                    .amount(testData.tournamentAmount)
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(testData.gameLaunchData.getDbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, request),
                    request);

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code")
            );
        });
    }
}