package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий невозможность выполнения рефанда для транзакций начисления выигрышей различных типов,
 * включая транзакции с нулевой суммой выигрыша.
 *
 * <p>Данный параметризованный тест проверяет поведение системы при попытке выполнить операцию рефанда,
 * указывая в качестве {@code betTransactionId} идентификатор транзакции начисления выигрыша
 * одного из следующих типов: {@link NatsGamblingTransactionOperation#WIN WIN},
 * {@link NatsGamblingTransactionOperation#JACKPOT JACKPOT}, или {@link NatsGamblingTransactionOperation#FREESPIN FREESPIN}.
 * Тест подтверждает, что система корректно обрабатывает такие запросы, отклоняя их,
 * поскольку операция рефанда применима только к транзакциям типа "ставка" (bet).</p>
 *
 * <p>Тест также включает сценарии с нулевой суммой выигрыша. Несмотря на то, что такие транзакции могут
 * интерпретироваться как отсутствие выигрыша или даже проигрыш (если регистрируются как win-транзакция
 * с нулевой суммой), ключевым аспектом является то, что рефанд применим исключительно
 * к транзакциям типа "ставка" (bet), а не к результатам этих ставок (выигрышам), независимо от их суммы.</p>
 *
 * <p><b>Последовательность действий для каждого набора параметров:</b></p>
 * <ol>
 *   <li>Регистрация игрока с начальным балансом.</li>
 *   <li>Создание игровой сессии.</li>
 *   <li>Совершение транзакции начисления выигрыша указанного типа ({@code win}, {@code jackpot}, {@code freespin})
 *       и суммы (включая нулевую) игроку (успешно).</li>
 *   <li>Попытка выполнения рефанда, используя {@code transactionId} транзакции выигрыша
 *       в качестве {@code betTransactionId} (ожидается ошибка).</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты для каждого набора параметров:</b></p>
 * <ul>
 *   <li>Начисление выигрыша (в том числе с нулевой суммой) выполняется успешно (HTTP 200 OK).</li>
 *   <li>Попытка рефанда для транзакции выигрыша (любого из тестируемых типов и сумм)
 *       должна быть отклонена с кодом {@code HTTP 400 BAD REQUEST}
 *       и содержать ошибку {@link GamblingErrors#REFUND_NOT_ALLOWED} (или аналогичную ошибку,
 *       указывающую, что исходная транзакция не является ставкой, для которой возможен рефанд).</li>
 * </ul>
 *
 * <p><b>Тестируемые типы транзакций выигрыша ({@link NatsGamblingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code WIN} - Обычный выигрыш.</li>
 *   <li>{@code JACKPOT} - Выигрыш джекпота.</li>
 *   <li>{@code FREESPIN} - Выигрыш, полученный в результате бесплатных вращений.</li>
 * </ul>
 *
 * @see NatsGamblingTransactionOperation
 * @see GamblingErrors#REFUND_NOT_ALLOWED
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Негативные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundAfterWinParametrizedTest extends BaseParameterizedTest {
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");

    static Stream<Arguments> winLikeTransactionTypeProvider() {
        return Stream.of(
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.WIN
                ),
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.JACKPOT
                ),
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.FREESPIN
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.WIN
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.JACKPOT
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN
                )
        );
    }

    /**
     * Тестирует невозможность выполнения рефанда для транзакции выигрыша.
     *
     * @param winAmountParam Сумма исходной транзакции выигрыша (может быть 0).
     * @param winOperationTypeParam Тип операции выигрыша (WIN, JACKPOT, FREESPIN).
     */
    @ParameterizedTest(name = "транзакция типа [{1}] суммой [{0}] должна вызвать ошибку")
    @MethodSource("winLikeTransactionTypeProvider")
    @DisplayName("Попытка рефанда:")
    void test(BigDecimal winAmountParam, NatsGamblingTransactionOperation winOperationTypeParam) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            WinRequestBody winRequestBody;
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

        step("Manager API: Совершение исходной транзакции выигрыша", () -> {
            testData.winRequestBody = WinRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(winOperationTypeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, testData.winRequestBody),
                    testData.winRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code");
        });

        step("Manager API: Попытка выполнения рефанда для транзакции выигрыша", () -> {
            var refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(testData.winRequestBody.getTransactionId())
                    .roundId(testData.winRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.refund(
                            casinoId,
                            utils.createSignature(ApiEndpoints.REFUND, refundRequestBody),
                            refundRequestBody
                    ),
                    "manager_api.refund_after_win.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("manager_api.refund.after_win.error_validation",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.refund.status_code"),
                    () -> assertNotNull(error, "manager_api.refund.body"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getCode(), error.getCode(), "manager_api.refund.error_code"),
                    () -> assertEquals(GamblingErrors.REFUND_NOT_ALLOWED.getMessage(), error.getMessage(), "manager_api.refund.error_message")
            );
        });
    }
}