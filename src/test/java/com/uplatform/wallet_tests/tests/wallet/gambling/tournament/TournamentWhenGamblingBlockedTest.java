package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("Получение выигрыша в турнире игроком с заблокированным гемблингом")
    void shouldAwardTournamentWinAndVerify() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BigDecimal adjustmentAmount;
            BigDecimal tournamentAmount;
        }
        final TestContext ctx = new TestContext();

        ctx.adjustmentAmount = new BigDecimal("150.00");
        ctx.tournamentAmount = new BigDecimal("50.25");

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(ctx.adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии и проверка в БД", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("CAP API: Блокировка гемблинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(false)
                    .bettingEnabled(true)
                    .build();

            var response = capAdminClient.updateBlockers(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap.update_blockers.status_code");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            var request = TournamentRequestBody.builder()
                    .amount(ctx.tournamentAmount)
                    .playerId(ctx.registeredPlayer.getWalletData().getWalletUUID())
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(ctx.gameLaunchData.getDbGameSession().getProviderUuid())
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