package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Интеграционный параметризованный тест, проверяющий идемпотентную обработку дублирующихся ставок
 * при одновременной отправке для различных типов операций (BET, TIPS, FREESPIN) и сумм (включая нулевую).
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что при отправке двух абсолютно идентичных запросов на ставку одновременно, система корректно
 * обработает только один из них, а второй обработает идемпотентно. Тест ожидает, что оба запроса вернут
 * статус {@link HttpStatus#OK}, но с разными телами ответа: один с актуальным балансом после списания,
 * а второй — с нулевым балансом, подтверждая, что повторного списания не произошло.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации типа операции и суммы):</b></p>
 * <ol>
 *   <li><b>Подготовка:</b> Для каждого набора параметров создается новый игрок и игровая сессия.</li>
 *   <li><b>Одновременная отправка запросов:</b> Создается два идентичных `Callable`, выполняющих запрос {@code /bet}.
 *       Оба `Callable` отправляются на выполнение одновременно в пуле из двух потоков.</li>
 *   <li><b>Проверка ответов:</b>
 *       <ul>
 *           <li>Оба ответа должны вернуться со статусом {@link HttpStatus#OK}.</li>
 *           <li>{@code transactionId} в обоих ответах должен быть одинаковым.</li>
 *           <li>Баланс в одном из ответов должен соответствовать ожидаемому балансу после списания.</li>
 *           <li>Баланс во втором ответе должен быть равен {@link BigDecimal#ZERO}.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class DuplicateBetConcurrencyParametrizedTest extends BaseParameterizedTest {
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("1000.00");
    private static final BigDecimal defaultBetAmount = new BigDecimal("1.00");

    static Stream<Arguments> betOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.BET, BigDecimal.ZERO),

                Arguments.of(NatsGamblingTransactionOperation.TIPS, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, BigDecimal.ZERO),

                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO)
        );
    }

    @BeforeEach
    void setupForEachTest() {
        step("Default Step: Регистрация нового пользователя для теста", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии для теста", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.create_game_session");
        });
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("betOperationAndAmountProvider")
    @DisplayName("Идемпотентная обработка дублей ставок при одновременной отправке")
    void testConcurrentDuplicateBetsHandledIdempotently(NatsGamblingTransactionOperation operationParam, BigDecimal betAmountParam) throws InterruptedException {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        BigDecimal expectedBalanceAfterSuccessfulBet = initialAdjustmentAmount.subtract(betAmountParam);

        step(String.format("Manager API: Одновременная отправка дублирующихся ставок (тип: %s, сумма: %s)", operationParam, betAmountParam), () -> {

            var request = BetRequestBody.builder()
                    .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var betApiCall = (Callable<ResponseEntity<GamblingResponseBody>>) () -> managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, request),
                    request
            );

            var executor = Executors.newFixedThreadPool(2);
            var callables = List.of(betApiCall, betApiCall);
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

            assertAll("Проверка ответов на одновременные ставки",
                    () -> assertEquals(HttpStatus.OK, response1.getStatusCode(), "manager_api.response1.status_code"),
                    () -> assertEquals(HttpStatus.OK, response2.getStatusCode(), "manager_api.response2.status_code"),
                    () -> assertEquals(request.getTransactionId(), body1.getTransactionId(), "manager_api.response1.transaction_id"),
                    () -> assertEquals(request.getTransactionId(), body2.getTransactionId(), "manager_api.response2.transaction_id"),
                    () -> assertTrue(
                            actualBalances.stream().anyMatch(b -> b.compareTo(expectedBalanceAfterSuccessfulBet) == 0),
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