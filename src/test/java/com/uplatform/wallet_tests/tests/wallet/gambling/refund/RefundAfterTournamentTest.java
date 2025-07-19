package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий невозможность выполнения рефанда для транзакции турнирного выигрыша.
 *
 * <p>Данный тест проверяет поведение системы при попытке выполнить операцию рефанда,
 * указывая в качестве {@code betTransactionId} идентификатор транзакции начисления турнирного выигрыша.
 * Тест подтверждает, что система корректно обрабатывает такие запросы, отклоняя их,
 * поскольку операция рефанда применима только к транзакциям типа "ставка" (bet).</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация игрока с начальным балансом.</li>
 *   <li>Создание игровой сессии.</li>
 *   <li>Начисление турнирного выигрыша игроку (успешно).</li>
 *   <li>Попытка выполнения рефанда, используя {@code transactionId} турнирного выигрыша
 *       в качестве {@code betTransactionId} (ожидается ошибка).</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Начисление турнирного выигрыша выполняется успешно (HTTP 200 OK).</li>
 *   <li>Попытка рефанда для транзакции турнирного выигрыша
 *       должна быть отклонена с кодом {@code HTTP 400 BAD REQUEST}
 *       и содержать ошибку {@link GamblingErrors#REFUND_NOT_ALLOWED} (или аналогичную,
 *       указывающую на то, что исходная транзакция не является ставкой, для которой возможен рефанд).</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Негативные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundAfterTournamentTest extends BaseTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal tournamentAmount = generateBigDecimalAmount(initialAdjustmentAmount);

    @Test
    @DisplayName("Рефанд для турнирного выигрыша должен быть отклонен")
    void test() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequest;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            ctx.tournamentRequest = TournamentRequestBody.builder()
                    .amount(tournamentAmount)
                    .playerId(ctx.registeredPlayer.getWalletData().getWalletUUID())
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(ctx.gameLaunchData.getDbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.tournamentRequest),
                    ctx.tournamentRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code");
        });

        step("Manager API: Попытка выполнения рефанда для турнирного выигрыша", () -> {
            var request = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(tournamentAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.tournamentRequest.getTransactionId())
                    .roundId(ctx.tournamentRequest.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.refund(
                            casinoId,
                            utils.createSignature(ApiEndpoints.REFUND, request),
                            request
                    ),
                    "manager_api.refund_after_tournament.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("manager_api.refund.after_tournament.error_validation",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.refund.status_code"),
                    () -> assertNotNull(error, "manager_api.refund.body"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getCode(), error.getCode(), "manager_api.refund.error_code"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getMessage(), error.getMessage(), "manager_api.refund.error_message")
            );
        });
    }
}