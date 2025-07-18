package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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

import static com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors.ROLLBACK_NOT_ALLOWED;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий невозможность выполнения повторного роллбэка
 * для транзакции ставки, которая уже была отменена, для игрока с заблокированным гемблингом.
 *
 * <p>Данный тест проверяет поведение системы при попытке выполнить операцию роллбэка
 * для транзакции ставки, которая уже была отменена с помощью предыдущей операции роллбэка.
 * Тест подтверждает, что система корректно блокирует повторную попытку роллбэка.</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация игрока с начальным балансом.</li>
 *   <li>Создание игровой сессии.</li>
 *   <li>Выполнение ставки.</li>
 *   <li>Выполнение первого роллбэка для совершенной ставки (успешно).</li>
 *   <li>Попытка выполнения второго роллбэка для той же исходной ставки (ожидается ошибка).</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Первая операция роллбэка выполняется успешно (HTTP 200 OK).</li>
 *   <li>Попытка повторного роллбэка для ставки, по которой уже был выполнен роллбэк,
 *   должна быть отклонена с кодом
 *       {@code HTTP 400 BAD REQUEST} и содержать ошибку
 *       {@link GamblingErrors#PLAYER_BLOCKED}.
 *       Сообщение об ошибке также может указывать на то, что роллбэк уже был обработан.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackAfterRollbackTest extends BaseTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal betAmount = generateBigDecimalAmount(initialAdjustmentAmount);

    @Test
    @DisplayName("Попытка повторного роллбэка ставки")
    void testRepeatedRollbackForBlockedPlayerReturnsError() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody firstRollbackRequestBody;
        }
        final TestData testData = new TestData();
        final BigDecimal rollbackAmount = betAmount;

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение ставки", () -> {
            testData.betRequestBody = BetRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, testData.betRequestBody),
                    testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("Manager API: Выполнение первого роллбэка для ставки", () -> {
            testData.firstRollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(rollbackAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(testData.betRequestBody.getTransactionId())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(testData.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, testData.firstRollbackRequestBody),
                    testData.firstRollbackRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.first_rollback.status_code");
        });

        step("Manager API: Попытка выполнения повторного роллбэка для той же ставки", () -> {
            RollbackRequestBody secondRollbackAttemptBody = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(rollbackAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(testData.betRequestBody.getTransactionId())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(testData.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.rollback(
                            casinoId,
                            utils.createSignature(ApiEndpoints.ROLLBACK, secondRollbackAttemptBody),
                            secondRollbackAttemptBody),
                    "Попытка повторного роллбэка после блокировки игрока должна вызвать исключение FeignException"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки при попытке повторного роллбэка для заблокированного игрока",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.second_rollback.status_code"),
                    () -> assertEquals(GamblingErrors.PLAYER_BLOCKED.getCode(), error.getCode(), "manager_api.second_rollback.error_code"),
                    () -> assertEquals(ROLLBACK_NOT_ALLOWED.getCode(), error.getCode(), "manager_api.second_rollback.error_code"),
                    () -> assertEquals(ROLLBACK_NOT_ALLOWED.getMessage(), error.getMessage(), "manager_api.second_rollback.error_message")
            );
        });
    }
}