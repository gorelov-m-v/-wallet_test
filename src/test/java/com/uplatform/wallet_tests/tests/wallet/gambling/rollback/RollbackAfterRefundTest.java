package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.UUID;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий невозможность выполнения роллбэка для ставки,
 * по которой ранее был успешно выполнен рефанд.
 *
 * <p>Данный тест проверяет поведение системы при попытке выполнить операцию роллбэка
 * для исходной ставки ({@code betTransactionId}), которая уже была возвращена игроку
 * через операцию рефанда. Тест подтверждает, что система корректно блокирует такую
 * операцию, чтобы избежать двойного возврата средств пользователю (один раз через рефанд,
 * второй раз через роллбэк).</p>
 *
 * <p><b>Последовательность действий:</b></p>
 * <ol>
 *   <li>Регистрация игрока с начальным балансом.</li>
 *   <li>Создание игровой сессии.</li>
 *   <li>Выполнение ставки.</li>
 *   <li>Выполнение рефанда для сделанной ставки.</li>
 *   <li>Попытка выполнения роллбэка для той же исходной ставки (ожидается ошибка).</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты:</b></p>
 * <ul>
 *   <li>Ставка и рефанд выполняются успешно (HTTP 200 OK).</li>
 *   <li>Попытка роллбэка ставки, по которой был выполнен рефанд,
 *       должна быть отклонена с кодом {@code HTTP 400 BAD REQUEST}
 *       и содержать ошибку {@link GamblingErrors#ROLLBACK_NOT_ALLOWED} (или аналогичную,
 *       указывающую на то, что транзакция не может быть отменена, так как уже была возвращена/обработана).</li>
 * </ul>
 */
@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
@TmsLink("")
class RollbackAfterRefundTest {
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal betAmount = generateBigDecimalAmount(initialAdjustmentAmount);

    @Test
    @DisplayName("Попытка роллбэка ставки после выполнения рефанда по ней")
    void testRollbackAfterRefundReturnsError() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RefundRequestBody refundRequestBody;
            RollbackRequestBody rollbackRequestBody;
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

        step("Manager API: Выполнение рефанда по ставке", () -> {
            testData.refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(testData.betRequestBody.getTransactionId())
                    .roundId(testData.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .build();

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, testData.refundRequestBody),
                    testData.refundRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code");
        });

        step("Manager API: Попытка выполнения роллбэка для ставки, по которой был сделан рефанд", () -> {
            testData.rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
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
                            utils.createSignature(ApiEndpoints.ROLLBACK, testData.rollbackRequestBody),
                            testData.rollbackRequestBody
                    ),
                    "Попытка роллбэка ставки после рефанда должна вызвать исключение FeignException"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки при попытке роллбэка после рефанда",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.rollback_after_refund.status_code"),
                    () -> assertNotNull(error, "manager_api.rollback_after_refund.body"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getCode(), error.getCode(), "manager_api.rollback_after_refund.error_code"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getMessage(), error.getMessage(), "manager_api.rollback_after_refund.error_message")
            );
        });
    }
}