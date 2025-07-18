package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный параметризованный тест, проверяющий API ответ при роллбэке ставки (исходной транзакции),
 * которая была вытеснена из кэша Redis. Тест проверяет различные типы и суммы исходных транзакций,
 * включая нулевые. Определение вытесненной транзакции происходит динамически.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager успешно обрабатывает запрос на роллбэк исходной транзакции
 * (различных типов {@link NatsGamblingTransactionOperation#BET}, {@link NatsGamblingTransactionOperation#TIPS},
 * {@link NatsGamblingTransactionOperation#FREESPIN} и сумм), даже если информация о данной транзакции
 * отсутствует в Redis, предполагая, что система найдет ее в "холодном хранилище".
 * Основное внимание уделяется корректности ответа API.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации параметров):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок. Начальный баланс фиксируется.</li>
 *   <li><b>Создание игровой сессии.</b></li>
 *   <li><b>Совершение исходных транзакций:</b> Через API совершается {@code maxGamblingCountInRedis + 1} транзакций
 *       с параметризованной суммой и типом (BET, TIPS, FREESPIN). Сохраняются запросы этих транзакций.
 *       Ожидаемый баланс рассчитывается после каждой транзакции.
 *       Баланс после всех транзакций (подтвержденный API последней транзакции) запоминается.</li>
 *   <li><b>Получение Sequence последней транзакции:</b> Через NATS ожидается событие от последней сделанной транзакции
 *       для получения ее {@code sequence number}. Это необходимо для корректного запроса к Redis.</li>
 *   <li><b>Определение вытесненной транзакции:</b> Запрашиваются данные из Redis.
 *       Сравнивается список ID всех сделанных транзакций с ID транзакций в Redis.
 *       Определяется ID транзакции, которая отсутствует в Redis (вытесненная).
 *       Проверяется, что вытеснена ровно одна транзакция.</li>
 *   <li><b>Роллбэк вытесненной транзакции:</b> Через API выполняется роллбэк на определенную вытесненную транзакцию
 *       (с ее исходной суммой и типом).</li>
 *   <li><b>Проверка ответа API роллбэка:</b> Проверяется статус-код, ID транзакции роллбэка и итоговый баланс в ответе.</li>
 * </ol>
 *
 * <p><b>Параметры теста:</b></p>
 * <ul>
 *   <li>Сумма исходной транзакции ({@code betAmountParam}): Может быть ненулевой или нулевой.</li>
 *   <li>Тип исходной транзакции ({@code typeParam}): {@link NatsGamblingTransactionOperation#BET},
 *       {@link NatsGamblingTransactionOperation#TIPS}, {@link NatsGamblingTransactionOperation#FREESPIN}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Позитивные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackDisplacedBetParameterizedTest extends BaseParameterizedTest {
    @Autowired private WalletRedisClient redisClient;
    @Autowired private NatsClient natsClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private TestUtils utils;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("1000.00");

    static Stream<Arguments> transactionTypeAndAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(new BigDecimal("1.00")),
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(new BigDecimal("1.00")),
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(new BigDecimal("1.00")),
                        NatsGamblingTransactionOperation.FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN
                )
        );
    }

    @ParameterizedTest(name = "тип исходной транзакции = {1}, сумма = {0}")
    @MethodSource("transactionTypeAndAmountProvider")
    @DisplayName("Роллбэк транзакции, вытесненной из Redis")
    void testApiRollbackForDynamicallyIdentifiedDisplacedBet(
            BigDecimal betAmountParam, NatsGamblingTransactionOperation typeParam) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final int maxGamblingCountInRedis = 50;

        final int currentTransactionCountToMake = maxGamblingCountInRedis + 1;

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<BetRequestBody> madeBetsRequests = new ArrayList<>();
            String lastMadeBetTransactionId;
            BetRequestBody betToRollbackRequest;
            NatsMessage<NatsGamblingEventPayload> lastBetNatsEvent;
            BigDecimal currentCalculatedBalance;
            BigDecimal balanceFromApiAfterAllBets;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            testData.currentCalculatedBalance = testData.registeredPlayer.getWalletData().getBalance();
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение " + currentTransactionCountToMake + " исходных транзакций типа " + typeParam + " на сумму " + betAmountParam, () -> {
            for (int i = 0; i < currentTransactionCountToMake; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == currentTransactionCountToMake - 1) {
                    testData.lastMadeBetTransactionId = transactionId;
                }

                var betRequestBody = BetRequestBody.builder()
                        .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                        .amount(betAmountParam)
                        .transactionId(transactionId)
                        .type(typeParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(false)
                        .build();
                testData.madeBetsRequests.add(betRequestBody);

                var currentBetNumber = i + 1;
                var currentTxId = transactionId;

                step("Совершение транзакции #" + currentBetNumber + " с ID: " + currentTxId, () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);

                    testData.currentCalculatedBalance = testData.currentCalculatedBalance.subtract(betAmountParam);
                    assertAll("Проверка ответа API для транзакции #" + currentBetNumber,
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertEquals(currentTxId, response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, testData.currentCalculatedBalance.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
                    );

                    if (currentBetNumber == currentTransactionCountToMake) {
                        testData.balanceFromApiAfterAllBets = response.getBody().getBalance();
                    }
                });
            }

            assertEquals(currentTransactionCountToMake, testData.madeBetsRequests.size(), "bet.list.size");
        });

        step("NATS: Ожидание NATS-события betted_from_gamble для последней транзакции", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, natsTypeHeader) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(natsTypeHeader) &&
                            testData.lastMadeBetTransactionId.equals(payload.getUuid());

            testData.lastBetNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(testData.lastBetNatsEvent, "nats.betted_from_gamble");
        });

        step("Redis: Определение вытесненной транзакции", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.lastBetNatsEvent.getSequence());

            var gamblingTransactionsInRedis = aggregate.getGambling();
            var transactionIdsCurrentlyInRedis = gamblingTransactionsInRedis.keySet();
            var displacedTransactionIds = testData.madeBetsRequests.stream()
                    .map(BetRequestBody::getTransactionId).collect(Collectors.toCollection(HashSet::new));
            displacedTransactionIds.removeAll(transactionIdsCurrentlyInRedis);

            assertEquals(1, displacedTransactionIds.size(), "redis.displaced_transaction.expected_single");
            var displacedTxId = displacedTransactionIds.iterator().next();

            testData.betToRollbackRequest = testData.madeBetsRequests.stream()
                    .filter(betReq -> betReq.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));

            assertEquals(0, betAmountParam.compareTo(testData.betToRollbackRequest.getAmount()), "bet.displaced.amount_mismatch");
            assertEquals(typeParam, testData.betToRollbackRequest.getType(), "bet.displaced.type_mismatch");
        });

        step("Manager API: Роллбэк вытесненной транзакции", () -> {

            RollbackRequestBody rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.betToRollbackRequest.getAmount())
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(testData.betToRollbackRequest.getTransactionId())
                    .roundId(testData.betToRollbackRequest.getRoundId())
                    .roundClosed(true)
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .build();

            var expectedBalanceInApiResponse = testData.balanceFromApiAfterAllBets.add(rollbackRequestBody.getAmount());

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, rollbackRequestBody),
                    rollbackRequestBody);

            assertAll("Проверка ответа API на роллбэк вытесненной транзакции",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code"),
                    () -> assertEquals(rollbackRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.rollback.body.transactionId"),
                    () -> assertEquals(0, expectedBalanceInApiResponse.compareTo(response.getBody().getBalance()), "manager_api.rollback.body.balance")
            );
        });
    }
}