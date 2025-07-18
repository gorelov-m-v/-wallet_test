package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
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
 * Интеграционный параметризованный тест, проверяющий API ответ при рефанде ставки,
 * которая была вытеснена из кэша Redis. Тест проверяет различные типы и суммы ставок,
 * включая нулевые. Определение вытесненной ставки происходит динамически.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager успешно обрабатывает запрос на рефанд ставки (различных типов и сумм),
 * даже если информация о данной ставке отсутствует в Redis, предполагая, что система
 * найдет ее в "холодном хранилище". Основное внимание уделяется корректности ответа API.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации параметров):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок. Начальный баланс фиксируется.</li>
 *   <li><b>Создание игровой сессии.</b></li>
 *   <li><b>Совершение ставок:</b> Через API совершается {@code maxGamblingCountInRedis + 1} ставок
 *       с параметризованной суммой и типом. Сохраняются запросы этих ставок.
 *       Ожидаемый баланс рассчитывается после каждой ставки.
 *       Баланс после всех ставок (подтвержденный API последней ставки) запоминается.</li>
 *   <li><b>Получение Sequence последней ставки:</b> Через NATS ожидается событие от последней сделанной ставки
 *       для получения ее {@code sequence number}. Это необходимо для корректного запроса к Redis.</li>
 *   <li><b>Определение вытесненной ставки:</b> Запрашиваются данные из Redis для агрегата кошелька.
 *       Сравнивается список ID всех сделанных ставок с ID ставок в Redis.
 *       Определяется ID ставки, которая отсутствует в Redis (вытесненная).
 *       Проверяется, что вытеснена ровно одна ставка.</li>
 *   <li><b>Рефанд вытесненной ставки:</b> Через API выполняется рефанд на определенную вытесненную ставку
 *       (с ее исходной суммой и типом).</li>
 *   <li><b>Проверка ответа API рефанда:</b> Проверяется статус-код, ID транзакции рефанда и итоговый баланс в ответе.</li>
 * </ol>
 *
 * <p><b>Параметры теста:</b></p>
 * <ul>
 *   <li>Сумма ставки ({@code betAmountParam}): Может быть ненулевой или нулевой.</li>
 *   <li>Тип ставки ({@code typeParam}): {@link NatsGamblingTransactionOperation#BET},
 *       {@link NatsGamblingTransactionOperation#TIPS}, {@link NatsGamblingTransactionOperation#FREESPIN}.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Позитивные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundDisplacedBetParameterizedTest extends BaseParameterizedTest {
    @Autowired private WalletRedisClient redisClient;
    @Autowired private NatsClient natsClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;

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

    @ParameterizedTest(name = "тип ставки = {1}, сумма ставки = {0}")
    @MethodSource("transactionTypeAndAmountProvider")
    @DisplayName("Рефанд ставки (разные типы и суммы), вытесненной из Redis")
    void testApiRefundForDynamicallyIdentifiedDisplacedBet(
            BigDecimal betAmountParam, NatsGamblingTransactionOperation typeParam) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final int maxGamblingCountInRedis = 50;

        final int currentTransactionCountToMake = maxGamblingCountInRedis + 1;

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<BetRequestBody> madeBetsRequests = new ArrayList<>();
            String lastMadeBetTransactionId;
            BetRequestBody betToRefundRequest;
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

        step("Manager API: Совершение " + currentTransactionCountToMake + " ставок типа " + typeParam + " на сумму " + betAmountParam, () -> {
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

                step("Совершение ставки #" + currentBetNumber + " с ID: " + currentTxId, () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);

                    assertNotNull(response.getBody(), "manager_api.bet.body_not_null");
                    testData.currentCalculatedBalance = testData.currentCalculatedBalance.subtract(betAmountParam);

                    assertAll("Проверка ответа API для ставки #" + currentBetNumber,
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

        step("NATS: Ожидание NATS-события betted_from_gamble для последней ставки", () -> {
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

        step("Redis: Определение вытесненной ставки", () -> {
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

            testData.betToRefundRequest = testData.madeBetsRequests.stream()
                    .filter(betReq -> betReq.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));

            assertEquals(0, betAmountParam.compareTo(testData.betToRefundRequest.getAmount()), "bet.displaced.amount_mismatch");
        });

        step("Manager API: Рефанд вытесненной ставки", () -> {

            RefundRequestBody refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.betToRefundRequest.getAmount())
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(testData.betToRefundRequest.getTransactionId())
                    .roundId(testData.betToRefundRequest.getRoundId())
                    .roundClosed(true)
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .build();

            var expectedBalanceInApiResponse = testData.balanceFromApiAfterAllBets.add(refundRequestBody.getAmount());

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, refundRequestBody),
                    refundRequestBody);

            assertAll("Проверка ответа API на рефанд вытесненной ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code"),
                    () -> assertEquals(refundRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.refund.body.transactionId"),
                    () -> assertEquals(0, expectedBalanceInApiResponse.compareTo(response.getBody().getBalance()), "manager_api.refund.body.balance")
            );
        });
    }
}