package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный параметризованный тест, проверяющий идемпотентную обработку дублирующихся выигрышей
 * при одновременной отправке для различных типов операций (WIN, FREESPIN, JACKPOT) и сумм (включая нулевую).
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что при отправке двух абсолютно идентичных запросов на выигрыш одновременно, система корректно
 * обработает только один из них, а второй обработает идемпотентно. Тест ожидает, что оба запроса вернут
 * статус {@link HttpStatus#OK}, но с разными телами ответа: один с актуальным балансом после начисления,
 * а второй — с нулевым балансом, подтверждая, что повторного начисления не произошло.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации типа операции и суммы):</b></p>
 * <ol>
 *   <li><b>Подготовка:</b> Для каждого набора параметров создается новый игрок, игровая сессия и совершается базовая
 *       ставка, к которой будет привязан выигрыш.</li>
 *   <li><b>Одновременная отправка запросов:</b> Создается два идентичных `Callable`, выполняющих запрос {@code /win}.
 *       Оба `Callable` отправляются на выполнение одновременно в пуле из двух потоков.</li>
 *   <li><b>Проверка ответов:</b>
 *       <ul>
 *           <li>Оба ответа должны вернуться со статусом {@link HttpStatus#OK}.</li>
 *           <li>{@code transactionId} в обоих ответах должен быть одинаковым.</li>
 *           <li>Баланс в одном из ответов должен соответствовать ожидаемому балансу после начисления выигрыша.</li>
 *           <li>Баланс во втором ответе должен быть равен {@link BigDecimal#ZERO}.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class DuplicateWinConcurrencyParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private BetRequestBody initialBetRequest;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("1000.00");
    private static final BigDecimal baseBetAmount = new BigDecimal("10.00");
    private static final BigDecimal defaultWinAmount = new BigDecimal("1.00");

    static Stream<Arguments> winOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.WIN, defaultWinAmount),
                Arguments.of(NatsGamblingTransactionOperation.WIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, defaultWinAmount),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, defaultWinAmount),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, BigDecimal.ZERO)
        );
    }

    @BeforeEach
    void setupForEachTest() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Default Step: Регистрация нового пользователя для теста", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии для теста", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.create_game_session");
        });

        step("Default Step: Совершение базовой ставки для привязки выигрыша", () -> {
            this.initialBetRequest = BetRequestBody.builder()
                    .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(baseBetAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, initialBetRequest),
                    initialBetRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "default_step.base_bet_status_code");
        });
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("winOperationAndAmountProvider")
    @DisplayName("Идемпотентная обработка дублей выигрышей при одновременной отправке")
    void testConcurrentDuplicateWinsHandledIdempotently(NatsGamblingTransactionOperation operationParam, BigDecimal winAmountParam) throws InterruptedException {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        BigDecimal balanceAfterBet = initialAdjustmentAmount.subtract(baseBetAmount);
        BigDecimal expectedBalanceAfterSuccessfulWin = balanceAfterBet.add(winAmountParam);

        step(String.format("Manager API: Одновременная отправка дублирующихся выигрышей (тип: %s, сумма: %s)", operationParam, winAmountParam), () -> {

            var request = WinRequestBody.builder()
                    .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(this.initialBetRequest.getRoundId())
                    .roundClosed(false)
                    .build();

            Callable<ResponseEntity<GamblingResponseBody>> winApiCall = () -> managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, request),
                    request
            );

            var executor = Executors.newFixedThreadPool(2);
            var callables = List.of(winApiCall, winApiCall);
            var futures = executor.invokeAll(callables);
            executor.shutdown();

            var results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            fail("Один из параллельных запросов завершился с исключением", e);
                            return null;
                        }
                    }).collect(Collectors.toList());

            var response1 = results.get(0);
            var response2 = results.get(1);
            var body1 = response1.getBody();
            var body2 = response2.getBody();

            assertNotNull(body1, "manager_api.response1.body_is_null");
            assertNotNull(body2, "manager_api.response2.body_is_null");

            Set<BigDecimal> actualBalances = Set.of(body1.getBalance(), body2.getBalance());

            assertAll("Проверка ответов на одновременные выигрыши",
                    () -> assertEquals(HttpStatus.OK, response1.getStatusCode(), "manager_api.response1.status_code"),
                    () -> assertEquals(HttpStatus.OK, response2.getStatusCode(), "manager_api.response2.status_code"),
                    () -> assertEquals(request.getTransactionId(), body1.getTransactionId(), "manager_api.response1.transaction_id"),
                    () -> assertEquals(request.getTransactionId(), body2.getTransactionId(), "manager_api.response2.transaction_id"),
                    () -> assertTrue(
                            actualBalances.stream().anyMatch(b -> b.compareTo(expectedBalanceAfterSuccessfulWin) == 0),
                            "manager_api.responses.balances.no_expected_balance"
                    ),
                    () -> assertTrue(
                            actualBalances.stream().anyMatch(b -> b.compareTo(BigDecimal.ZERO) == 0),
                            "manager_api.responses.balances.no_zero_balance"
                    )
            );
        });
    }
}