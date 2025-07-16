package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий функциональность отката ставок (роллбэка), включая ставки с нулевой суммой,
 * для игрока с заблокированным гемблингом в системе Wallet.
 *
 * <p>Данный параметризованный тест проверяет сценарий, когда игрок, у которого заблокирован
 * гемблинг (gamblingEnabled=false), но разрешен беттинг (bettingEnabled=true),
 * пытается откатить ставку различных типов (BET, TIPS, FREESPIN) и различных сумм, включая нулевые.
 * В данном сценарии игрок должен иметь возможность откатить ставку даже при заблокированном
 * гемблинге, так как операция отката рассматривается как отмена ранее принятой операции,
 * а не как новая игровая операция. Роллбэк нулевой ставки не изменяет баланс, но сама операция должна пройти успешно.</p>
 *
 * <p>Тест создает для каждого тестового сценария нового игрока, выполняет исходную ставку выбранного типа и суммы,
 * затем блокирует гемблинг через CAP API, и проверяет возможность отката ставки
 * при заблокированном гемблинге.</p>
 *
 * <p><b>Проверяемые типы исходных ставок, которые будут отменены:</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка в казино.</li>
 *   <li>{@code TIPS} - чаевые в казино.</li>
 *   <li>{@code FREESPIN} - бесплатное вращение.</li>
 * </ul>
 *
 * <p><b>Ожидаемый результат:</b> Система должна успешно обрабатывать откат всех видов ставок,
 * включая ставки с нулевой суммой, даже при заблокированной функциональности гемблинга.
 * Баланс игрока после ставки и последующего роллбэка должен вернуться к исходному значению.</p>
 */
@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Позитивные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
@TmsLink("NW-25")
class RollbackWhenGamblingBlockedParametrizedTest {
    @Autowired private CapAdminClient capAdminClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");

    static Stream<Arguments> rollbackScenarioProvider() {
        return Stream.of(
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.BET
                ),
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.TIPS
                ),
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.FREESPIN
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN
                )
        );
    }

    /**
     * Тестирует роллбэк ставки игроком с заблокированным гемблингом.
     *
     * @param betAmountParam Сумма исходной ставки (может быть 0), которая будет отменена.
     * @param betTypeParam Тип исходной операции ставки (BET, TIPS, FREESPIN), которая будет отменена.
     */
    @ParameterizedTest(name = "тип исходной ставки = {1}, сумма = {0}")
    @MethodSource("rollbackScenarioProvider")
    @DisplayName("Получение роллбэка игроком с заблокированным гемблингом (беттинг разрешен):")
    void testRollbackWhenGamblingBlocked(BigDecimal betAmountParam, NatsGamblingTransactionOperation betTypeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            BigDecimal expectedBalanceAfterRollback;
        }
        final TestData testData = new TestData();
        testData.expectedBalanceAfterRollback = initialAdjustmentAmount;

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение исходной транзакции (ставки)", () -> {
            testData.betRequestBody = BetRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(betTypeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, testData.betRequestBody),
                    testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
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
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });

        step("Manager API: Выполнение роллбэка транзакции", () -> {
            var rollbackRequestBody = com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
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
                    utils.createSignature(ApiEndpoints.ROLLBACK, rollbackRequestBody),
                    rollbackRequestBody);

            assertNotNull(response.getBody(), "manager_api.rollback.body_not_null");
            assertAll("Проверка статус-кода и тела ответа при роллбэке",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code"),
                    () -> assertEquals(rollbackRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.rollback.transaction_id"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRollback.compareTo(response.getBody().getBalance()), "manager_api.rollback.balance")
            );
        });
    }
}