package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.WalletDatabaseClient;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.kafka.client.WalletProjectionKafkaClient;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
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
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий функциональность отката ставок (роллбэка) в системе Wallet для азартных игр.
 *
 * <p>Данный параметризованный тест проверяет полный жизненный цикл операции отката ставки
 * различных типов (BET, TIPS, FREESPIN). Тест выполняет начальную ставку, а затем проверяет
 * корректность обработки отката этой ставки со всеми сопутствующими изменениями данных
 * во всех компонентах системы.</p>
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным состоянием,
 * включая создание нового игрока и игровой сессии, что обеспечивает надежность при параллельном выполнении.</p>
 *
 * <p><b>Проверяемые типы исходных операций:</b></p>
 * <ul>
 *   <li>{@link NatsGamblingTransactionOperation#BET} - откат обычной ставки</li>
 *   <li>{@link NatsGamblingTransactionOperation#TIPS} - откат чаевых</li>
 *   <li>{@link NatsGamblingTransactionOperation#FREESPIN} - откат бесплатных вращений</li>
 * </ul>
 *
 * <p><b>Проверяемые аспекты системы:</b></p>
 * <ul>
 *   <li><b>REST API:</b>
 *     <ul>
 *       <li>Выполнение исходной операции ставки ({@code /bet})</li>
 *       <li>Выполнение операции отката ставки ({@code /rollback})</li>
 *       <li>Корректность ответа API и обновленного баланса игрока</li>
 *     </ul>
 *   </li>
 *   <li><b>События:</b>
 *     <ul>
 *       <li>Генерация события {@code rollbacked_from_gamble} в NATS</li>
 *       <li>Корректное заполнение всех полей события</li>
 *       <li>Соблюдение правил направления транзакции (DEPOSIT) и типа операции (ROLLBACK)</li>
 *     </ul>
 *   </li>
 *   <li><b>База данных:</b>
 *     <ul>
 *       <li>Сохранение транзакции роллбэка в {@code gambling_projection_transaction_history}</li>
 *       <li>Обновление порогов выигрыша в {@code player_threshold_win}</li>
 *       <li>Корректные связи между исходной ставкой и роллбэком</li>
 *     </ul>
 *   </li>
 *   <li><b>Кэш:</b>
 *     <ul>
 *       <li>Обновление данных кошелька в Redis</li>
 *       <li>Корректный расчет баланса после роллбэка</li>
 *     </ul>
 *   </li>
 *   <li><b>Kafka: wallet.v8.projectionSource</b>
 *     <ul>
 *       <li>Трансляция события роллбэка в Kafka</li>
 *       <li>Идентичность событий в NATS и Kafka</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Бизнес-логика операции роллбэка:</b></p>
 * <ul>
 *   <li>При откате ставки игроку возвращается списанная ранее сумма</li>
 *   <li>Корректируется порог выигрыша с учетом отмененной ставки</li>
 *   <li>Транзакция роллбэка должна содержать ссылку на ID исходной ставки</li>
 *   <li>Раунд игры может быть помечен как закрытый при роллбэке</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Позитивные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackParametrizedTest extends BaseParameterizedTest {
    @Autowired private WalletProjectionKafkaClient walletProjectionKafkaClient;
    @Autowired private WalletRedisClient redisClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletDatabaseClient walletDatabaseClient;
    @Autowired private TestUtils utils;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final String expectedCurrencyRates = "1";

    static Stream<Arguments> rollbackAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
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

    /**
     @param rollbackAmountParam Сумма для исходной транзакции и последующего роллбэка
     @param operationTypeParam Тип исходной транзакции (BET, TIPS, FREESPIN)
     */
    @ParameterizedTest(name = "Роллбэк транзакции типа {1} суммой {0}")
    @MethodSource("rollbackAmountProvider")
    @DisplayName("Получение роллбэка игроком в игровой сессии для разных сумм и типов транзакций")
    void test(
            BigDecimal rollbackAmountParam,
            NatsGamblingTransactionOperation operationTypeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody rollbackRequestBody;
            NatsMessage<NatsGamblingEventPayload> rollbackEvent;
            BigDecimal adjustmentAmount;
            BigDecimal betAmount;
            BigDecimal rollbackAmount;
            String expectedCurrencyRates;
            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterRollback;
        }
        final TestData testData = new TestData();

        testData.rollbackAmount = rollbackAmountParam;
        testData.betAmount = rollbackAmountParam;
        testData.adjustmentAmount = initialAdjustmentAmount;
        testData.expectedCurrencyRates = expectedCurrencyRates;
        testData.expectedBalanceAfterBet = BigDecimal.ZERO
                .add(testData.adjustmentAmount)
                .subtract(testData.betAmount);
        testData.expectedBalanceAfterRollback = BigDecimal.ZERO
                .add(testData.adjustmentAmount)
                .subtract(testData.betAmount)
                .add(testData.rollbackAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(testData.adjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение исходной транзакции", () -> {
            testData.betRequestBody = BetRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationTypeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, testData.betRequestBody),
                    testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("Manager API: Выполнение роллбэка транзакции", () -> {
            testData.rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.rollbackAmount)
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
                    utils.createSignature(ApiEndpoints.ROLLBACK, testData.rollbackRequestBody),
                    testData.rollbackRequestBody);

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code"),
                    () -> assertEquals(testData.rollbackRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.rollback.transaction_id"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRollback.compareTo(response.getBody().getBalance()), "manager_api.rollback.balance")
            );
        });

        step("NATS: Проверка поступления события rollbacked_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.ROLLBACKED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            testData.rollbackRequestBody.getTransactionId().equals(payload.getUuid());

            testData.rollbackEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(testData.rollbackEvent, "nats.event.rollbacked_from_gamble");

            assertAll(
                    () -> assertEquals(testData.rollbackRequestBody.getTransactionId(), testData.rollbackEvent.getPayload().getUuid(), "nats.rollback.uuid"),
                    () -> assertEquals(testData.betRequestBody.getTransactionId(), testData.rollbackEvent.getPayload().getBetUuid(), "nats.rollback.bet_uuid"),
                    () -> assertEquals(testData.gameLaunchData.getDbGameSession().getGameSessionUuid(), testData.rollbackEvent.getPayload().getGameSessionUuid(), "nats.rollback.game_session_uuid"),
                    () -> assertEquals(testData.rollbackRequestBody.getRoundId(), testData.rollbackEvent.getPayload().getProviderRoundId(), "nats.rollback.provider_round_id"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), testData.rollbackEvent.getPayload().getCurrency(), "nats.rollback.currency"),
                    () -> assertEquals(0, testData.rollbackAmount.compareTo(testData.rollbackEvent.getPayload().getAmount()), "nats.rollback.amount"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_ROLLBACK, testData.rollbackEvent.getPayload().getType(), "nats.rollback.type"),
                    () -> assertTrue(testData.rollbackEvent.getPayload().isProviderRoundClosed(), "nats.rollback.round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, testData.rollbackEvent.getPayload().getMessage(), "nats.rollback.message_name"),
                    () -> assertNotNull(testData.rollbackEvent.getPayload().getCreatedAt(), "nats.rollback.created_at"),
                    () -> assertEquals(NatsTransactionDirection.DEPOSIT, testData.rollbackEvent.getPayload().getDirection(), "nats.rollback.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.ROLLBACK, testData.rollbackEvent.getPayload().getOperation(), "nats.rollback.operation"),
                    () -> assertEquals(platformNodeId, testData.rollbackEvent.getPayload().getNodeUuid(), "nats.rollback.node_uuid"),
                    () -> assertEquals(testData.gameLaunchData.getDbGameSession().getGameUuid(), testData.rollbackEvent.getPayload().getGameUuid(), "nats.rollback.game_uuid"),
                    () -> assertEquals(testData.gameLaunchData.getDbGameSession().getProviderUuid(), testData.rollbackEvent.getPayload().getProviderUuid(), "nats.rollback.provider_uuid"),
                    () -> assertTrue(testData.rollbackEvent.getPayload().getWageredDepositInfo().isEmpty(), "nats.rollback.wagered_deposit_info"),
                    () -> assertEquals(0, testData.rollbackAmount.compareTo(testData.rollbackEvent.getPayload().getCurrencyConversionInfo().getGameAmount()), "nats.rollback.game_amount"),
                    () -> assertFalse(testData.rollbackEvent.getPayload().getCurrencyConversionInfo().getGameCurrency().isEmpty(), "nats.rollback.game_currency"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), testData.rollbackEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getBaseCurrency(), "nats.rollback.base_currency"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), testData.rollbackEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getQuoteCurrency(), "nats.rollback.quote_currency"),
                    () -> assertEquals(testData.expectedCurrencyRates, testData.rollbackEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getValue(), "nats.rollback.currency_rates"),
                    () -> assertNotNull(testData.rollbackEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getUpdatedAt(), "nats.rollback.updated_at")
            );
        });

        step("DB Wallet: Проверка записи роллбэка в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.
                    findTransactionByUuidOrFail(testData.rollbackRequestBody.getTransactionId());

            assertNotNull(transaction, "db.transaction");

            assertAll(
                    () -> assertEquals(testData.rollbackEvent.getPayload().getUuid(), transaction.getUuid(), "db.transaction.uuid"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getPlayerUUID(), transaction.getPlayerUuid(), "db.transaction.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.transaction.date"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_ROLLBACK, transaction.getType(), "db.transaction.type"),
                    () -> assertEquals(NatsGamblingTransactionOperation.ROLLBACK, transaction.getOperation(), "db.transaction.operation"),
                    () -> assertEquals(testData.rollbackEvent.getPayload().getGameUuid(), transaction.getGameUuid(), "db.transaction.game_uuid"),
                    () -> assertEquals(testData.rollbackEvent.getPayload().getGameSessionUuid(), transaction.getGameSessionUuid(), "db.transaction.game_session_uuid"),
                    () -> assertEquals(testData.rollbackEvent.getPayload().getCurrency(), transaction.getCurrency(), "db.transaction.currency"),
                    () -> assertEquals(0, testData.rollbackAmount.compareTo(transaction.getAmount()), "db.transaction.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.transaction.created_at"),
                    () -> assertEquals(testData.rollbackEvent.getSequence(), transaction.getSeqnumber(), "db.transaction.seq_number"),
                    () -> assertEquals(testData.rollbackEvent.getPayload().isProviderRoundClosed(), transaction.isProviderRoundClosed(), "db.transaction.provider_round_closed")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win после rollback", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    testData.registeredPlayer.getWalletData().getPlayerUUID());

            assertNotNull(threshold, "db.threshold");

            assertAll(
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getPlayerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, testData.betAmount.negate().add(testData.rollbackEvent.getPayload().getAmount()).compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("Kafka: Проверка поступления сообщения о роллбэке в топик wallet.v8.projectionSource", () -> {
            var message = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    testData.rollbackEvent.getSequence());

            assertNotNull(message, "kafka.message");
            assertTrue(utils.areEquivalent(message, testData.rollbackEvent), "kafka.message.equivalent_to_nats");
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька после роллбэка", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.rollbackEvent.getSequence());

            assertNotNull(aggregate, "redis.aggregate");

            assertAll(
                    () -> assertEquals(testData.rollbackEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.aggregate.seq_number"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRollback.compareTo(aggregate.getBalance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRollback.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.aggregate.available_balance"),
                    () -> assertTrue(aggregate.getGambling().containsKey(testData.rollbackEvent.getPayload().getUuid()), "redis.aggregate.gambling_contains_uuid"),
                    () -> assertEquals(0, testData.rollbackAmount.compareTo(aggregate.getGambling().get(testData.rollbackEvent.getPayload().getUuid()).getAmount()), "redis.aggregate.gambling_amount"),
                    () -> assertNotNull(aggregate.getGambling().get(testData.rollbackEvent.getPayload().getUuid()).getCreatedAt(), "redis.aggregate.gambling_created_at")
            );
        });
    }
}