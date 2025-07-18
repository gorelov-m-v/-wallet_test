package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий невозможность выполнения роллбэка для транзакции турнирного выигрыша.
 *
 * <p>Данный тест проверяет поведение системы при попытке выполнить операцию роллбэка,
 * указывая в качестве {@code rollbackTransactionId} идентификатор транзакции начисления турнирного выигрыша.
 * Тест подтверждает, что система корректно обрабатывает такие запросы, отклоняя их,
 * поскольку операция роллбэка применима только к транзакциям типа "ставка" (bet).</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация игрока с начальным балансом.</li>
 *   <li>Создание игровой сессии.</li>
 *   <li>Начисление турнирного выигрыша игроку (успешно).</li>
 *   <li>Попытка выполнения роллбэка, используя {@code transactionId} турнирного выигрыша
 *       в качестве {@code rollbackTransactionId} (ожидается ошибка).</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Начисление турнирного выигрыша выполняется успешно (HTTP 200 OK).</li>
 *   <li>Попытка роллбэка для транзакции турнирного выигрыша
 *       должна быть отклонена с кодом {@code HTTP 400 BAD REQUEST}
 *       и содержать ошибку (например, {@link GamblingErrors#ROLLBACK_NOT_ALLOWED} или аналогичную,
 *       указывающую на то, что исходная транзакция не является ставкой, для которой возможен роллбэк).</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackAfterTournamentTest extends BaseTest {
    @Autowired private ManagerClient managerClient;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal tournamentAmount = generateBigDecimalAmount(initialAdjustmentAmount);

    @Test
    @DisplayName("Роллбэк для турнирного выигрыша должен быть отклонен")
    void testRollbackForTournamentTransactionReturnsError() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequest;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            testData.tournamentRequest = TournamentRequestBody.builder()
                    .amount(tournamentAmount)
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(testData.gameLaunchData.getDbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, testData.tournamentRequest),
                    testData.tournamentRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code");
        });

        step("Manager API: Попытка выполнения роллбэка для турнирного выигрыша", () -> {
            var request = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(tournamentAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(testData.tournamentRequest.getTransactionId())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(testData.tournamentRequest.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.rollback(
                            casinoId,
                            utils.createSignature(ApiEndpoints.ROLLBACK, request),
                            request
                    ),
                    "manager_api.rollback_after_tournament.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки при попытке роллбэка для турнирного выигрыша",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.rollback.status_code"),
                    () -> assertNotNull(error, "manager_api.rollback.body"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getCode(), error.getCode(), "manager_api.rollback.error_code"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getMessage(), error.getMessage(), "manager_api.rollback.error_message")
            );
        });
    }
}