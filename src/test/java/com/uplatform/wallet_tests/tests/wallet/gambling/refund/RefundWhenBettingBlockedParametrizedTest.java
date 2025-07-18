package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
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
 * Интеграционный тест, проверяющий функциональность возврата ставок (рефанда), включая ставки с нулевой суммой,
 * для игрока с заблокированным беттингом в системе Wallet.
 *
 * <p>Данный параметризованный тест проверяет сценарий, когда игрок, у которого заблокирован
 * беттинг (bettingEnabled=false), но разрешен гемблинг (gamblingEnabled=true),
 * пытается получить возврат ставки различных типов (BET, TIPS, FREESPIN) и различных сумм, включая нулевые.
 * В данном сценарии возврат ставок должен успешно проходить, так как блокировка беттинга
 * относится только к спортивным ставкам и не должна влиять на операции в казино.</p>
 *
 * <p>Тест создает для каждого тестового сценария нового игрока, выполняет исходную ставку (в том числе нулевую),
 * затем блокирует беттинг через CAP API, и проверяет возможность получения возврата ставки
 * при заблокированном беттинге. Рефанд нулевой ставки, по сути, не изменяет баланс, но сама операция
 * должна быть обработана успешно, если исходная "ставка" была нулевой.</p>
 *
 * <p><b>Проверяемые типы исходных ставок:</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка.</li>
 *   <li>{@code TIPS} - чаевые.</li>
 *   <li>{@code FREESPIN} - бесплатное вращение.</li>
 * </ul>
 *
 * <p><b>Ожидаемый результат:</b> Система должна успешно обрабатывать возврат всех видов ставок в казино,
 * включая ставки с нулевой суммой, несмотря на блокировку беттинга у игрока. Баланс игрока
 * после ставки и последующего рефанда должен вернуться к исходному значению.</p>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Позитивные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundWhenBettingBlockedParametrizedTest extends BaseParameterizedTest {
    @Autowired private CapAdminClient capAdminClient;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");

    static Stream<Arguments> refundTypeProvider() {
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
     * Тестирует рефанд ставки игроком с заблокированным беттингом.
     *
     * @param betAmountParam Сумма ставки (может быть 0).
     * @param typeParam Тип исходной операции ставки (BET, TIPS, FREESPIN).
     */
    @ParameterizedTest(name = "тип = {1}, сумма {0}")
    @MethodSource("refundTypeProvider")
    @DisplayName("Получение рефанда игроком с заблокированным беттингом (гемблинг разрешен):")
    void test(BigDecimal betAmountParam, NatsGamblingTransactionOperation typeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            BigDecimal expectedBalanceAfterRefund;
        }
        final TestData testData = new TestData();
        testData.expectedBalanceAfterRefund = initialAdjustmentAmount;

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
                    .type(typeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, testData.betRequestBody),
                    testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("CAP API: Блокировка беттинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(true)
                    .bettingEnabled(false)
                    .build();

            var response = capAdminClient.updateBlockers(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });

        step("Manager API: Выполнение рефанда транзакции", () -> {
            var refundRequestBody = com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
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
                    utils.createSignature(ApiEndpoints.REFUND, refundRequestBody),
                    refundRequestBody);

            assertNotNull(response.getBody(), "manager_api.refund.body_not_null");
            assertAll("Проверка статус-кода и тела ответа при рефанде",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code"),
                    () -> assertEquals(refundRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.refund.transaction_id"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRefund.compareTo(response.getBody().getBalance()), "manager_api.refund.balance")
            );
        });
    }
}