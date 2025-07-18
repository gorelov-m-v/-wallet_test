package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.WalletDatabaseClient;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.kafka.client.WalletProjectionKafkaClient;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
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
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий функциональность возврата ставок (рефанда) в системе Wallet для азартных игр.
 *
 * <p>Данный параметризованный тест проверяет полный жизненный цикл операции возврата ставки
 * различных типов (BET, TIPS, FREESPIN). Тест выполняет начальную ставку, а затем проверяет
 * корректность обработки возврата этой ставки со всеми сопутствующими изменениями данных
 * во всех компонентах системы.</p>
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным состоянием,
 * включая создание нового игрока и игровой сессии, что обеспечивает надежность при параллельном выполнении.</p>
 *
 * <p><b>Проверяемые типы исходных операций:</b></p>
 * <ul>
 *   <li>{@link NatsGamblingTransactionOperation#BET} - возврат обычной ставки</li>
 *   <li>{@link NatsGamblingTransactionOperation#TIPS} - возврат чаевых</li>
 *   <li>{@link NatsGamblingTransactionOperation#FREESPIN} - возврат бесплатных вращений</li>
 * </ul>
 *
 * <p><b>Проверяемые аспекты системы:</b></p>
 * <ul>
 *   <li><b>REST API:</b>
 *     <ul>
 *       <li>Выполнение исходной операции ставки ({@code /bet})</li>
 *       <li>Выполнение операции возврата ставки ({@code /refund})</li>
 *       <li>Корректность ответа API и обновленного баланса игрока</li>
 *     </ul>
 *   </li>
 *   <li><b>События:</b>
 *     <ul>
 *       <li>Генерация события {@code refunded_from_gamble} в NATS</li>
 *       <li>Корректное заполнение всех полей события</li>
 *       <li>Соблюдение правил направления транзакции (DEPOSIT) и типа операции (REFUND)</li>
 *     </ul>
 *   </li>
 *   <li><b>База данных:</b>
 *     <ul>
 *       <li>Сохранение транзакции рефанда в {@code gambling_projection_transaction_history}</li>
 *       <li>Обновление порогов выигрыша в {@code player_threshold_win}</li>
 *       <li>Корректные связи между исходной ставкой и рефандом</li>
 *     </ul>
 *   </li>
 *   <li><b>Кэш:</b>
 *     <ul>
 *       <li>Обновление данных кошелька в Redis</li>
 *       <li>Корректный расчет баланса после рефанда</li>
 *     </ul>
 *   </li>
 *   <li><b>Kafka: wallet.v8.projectionSource</b>
 *     <ul>
 *       <li>Трансляция события рефанда в Kafka</li>
 *       <li>Идентичность событий в NATS и Kafka</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Бизнес-логика операции рефанда:</b></p>
 * <ul>
 *   <li>При возврате ставки игроку возвращается списанная ранее сумма</li>
 *   <li>Корректируется порог выигрыша с учетом отмененной ставки</li>
 *   <li>Транзакция рефанда должна содержать ссылку на ID исходной ставки</li>
 *   <li>Раунд игры может быть помечен как закрытый при рефанде</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/refund")
@Suite("Позитивные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundParametrizedTest extends BaseParameterizedTest {
    @Autowired private WalletProjectionKafkaClient walletProjectionKafkaClient;
    @Autowired private WalletRedisClient redisClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private WalletDatabaseClient walletDatabaseClient;
    @Autowired private NatsClient natsClient;
    @Autowired private TestUtils utils;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final String expectedCurrencyRates = "1";

    static Stream<Arguments> refundAmountProvider() {
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
     @param refundAmountParam Сумма для исходной транзакции и последующего рефанда
     @param operationTypeParam Тип исходной транзакции (BET, TIPS, FREESPIN)
     */
    @ParameterizedTest(name = "Рефанд транзакции типа {1} суммой {0}")
    @MethodSource("refundAmountProvider")
    @DisplayName("Получение рефанда игроком в игровой сессии для разных сумм")
    void test(BigDecimal refundAmountParam, NatsGamblingTransactionOperation operationTypeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RefundRequestBody refundRequestBody;
            NatsMessage<NatsGamblingEventPayload> refundEvent;
            BigDecimal adjustmentAmount;
            BigDecimal betAmount;
            BigDecimal refundAmount;
            String expectedCurrencyRates;
            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterRefund;
        }
        final TestData testData = new TestData();

        testData.refundAmount = refundAmountParam;
        testData.betAmount = refundAmountParam;
        testData.adjustmentAmount = initialAdjustmentAmount;
        testData.expectedCurrencyRates = expectedCurrencyRates;
        testData.expectedBalanceAfterBet = BigDecimal.ZERO
                .add(testData.adjustmentAmount)
                .subtract(testData.betAmount);
        testData.expectedBalanceAfterRefund = BigDecimal.ZERO
                .add(testData.adjustmentAmount)
                .subtract(testData.betAmount)
                .add(testData.refundAmount);

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

        step("Manager API: Выполнение рефанда транзакции", () -> {
            testData.refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.refundAmount)
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

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code"),
                    () -> assertEquals(testData.refundRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.refund.transaction_id"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRefund.compareTo(response.getBody().getBalance()), "manager_api.refund.balance")
            );
        });

        step("NATS: Проверка поступления события refunded_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.REFUNDED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            testData.refundRequestBody.getTransactionId().equals(payload.getUuid());

            testData.refundEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(testData.refundEvent, "nats.event.refunded_from_gamble");

            assertAll(
                    () -> assertEquals(testData.refundRequestBody.getTransactionId(), testData.refundEvent.getPayload().getUuid(), "nats.refund.uuid"),
                    () -> assertEquals(testData.betRequestBody.getTransactionId(), testData.refundEvent.getPayload().getBetUuid(), "nats.refund.bet_uuid"),
                    () -> assertEquals(testData.gameLaunchData.getDbGameSession().getGameSessionUuid(), testData.refundEvent.getPayload().getGameSessionUuid(), "nats.refund.game_session_uuid"),
                    () -> assertEquals(testData.refundRequestBody.getRoundId(), testData.refundEvent.getPayload().getProviderRoundId(), "nats.refund.provider_round_id"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), testData.refundEvent.getPayload().getCurrency(), "nats.refund.currency"),
                    () -> assertEquals(0, testData.refundAmount.compareTo(testData.refundEvent.getPayload().getAmount()), "nats.refund.amount"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_REFUND, testData.refundEvent.getPayload().getType(), "nats.refund.type"),
                    () -> assertTrue(testData.refundEvent.getPayload().isProviderRoundClosed(), "nats.refund.round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, testData.refundEvent.getPayload().getMessage(), "nats.refund.message_name"),
                    () -> assertNotNull(testData.refundEvent.getPayload().getCreatedAt(), "nats.refund.created_at"),
                    () -> assertEquals(NatsTransactionDirection.DEPOSIT, testData.refundEvent.getPayload().getDirection(), "nats.refund.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.REFUND, testData.refundEvent.getPayload().getOperation(), "nats.refund.operation"),
                    () -> assertEquals(platformNodeId, testData.refundEvent.getPayload().getNodeUuid(), "nats.refund.node_uuid"),
                    () -> assertEquals(testData.gameLaunchData.getDbGameSession().getGameUuid(), testData.refundEvent.getPayload().getGameUuid(), "nats.refund.game_uuid"),
                    () -> assertEquals(testData.gameLaunchData.getDbGameSession().getProviderUuid(), testData.refundEvent.getPayload().getProviderUuid(), "nats.refund.provider_uuid"),
                    () -> assertTrue(testData.refundEvent.getPayload().getWageredDepositInfo().isEmpty(), "nats.refund.wagered_deposit_info"),
                    () -> assertEquals(0, testData.refundAmount.compareTo(testData.refundEvent.getPayload().getCurrencyConversionInfo().getGameAmount()), "nats.refund.game_amount"),
                    () -> assertFalse(testData.refundEvent.getPayload().getCurrencyConversionInfo().getGameCurrency().isEmpty(), "nats.refund.game_currency"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), testData.refundEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getBaseCurrency(), "nats.refund.base_currency"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), testData.refundEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getQuoteCurrency(), "nats.refund.quote_currency"),
                    () -> assertEquals(testData.expectedCurrencyRates, testData.refundEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getValue(), "nats.refund.currency_rates"),
                    () -> assertNotNull(testData.refundEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getUpdatedAt(), "nats.refund.updated_at")
            );
        });

        step("DB Wallet: Проверка записи рефанда в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.
                    findTransactionByUuidOrFail(testData.refundRequestBody.getTransactionId());

            assertNotNull(transaction, "db.transaction");

            assertAll(
                    () -> assertEquals(testData.refundEvent.getPayload().getUuid(), transaction.getUuid(), "db.transaction.uuid"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getPlayerUUID(), transaction.getPlayerUuid(), "db.transaction.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.transaction.date"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_REFUND, transaction.getType(), "db.transaction.type"),
                    () -> assertEquals(NatsGamblingTransactionOperation.REFUND, transaction.getOperation(), "db.transaction.operation"),
                    () -> assertEquals(testData.refundEvent.getPayload().getGameUuid(), transaction.getGameUuid(), "db.transaction.game_uuid"),
                    () -> assertEquals(testData.refundEvent.getPayload().getGameSessionUuid(), transaction.getGameSessionUuid(), "db.transaction.game_session_uuid"),
                    () -> assertEquals(testData.refundEvent.getPayload().getCurrency(), transaction.getCurrency(), "db.transaction.currency"),
                    () -> assertEquals(0, testData.refundAmount.compareTo(transaction.getAmount()), "db.transaction.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.transaction.created_at"),
                    () -> assertEquals(testData.refundEvent.getSequence(), transaction.getSeqnumber(), "db.transaction.seq_number"),
                    () -> assertEquals(testData.refundEvent.getPayload().isProviderRoundClosed(), transaction.isProviderRoundClosed(), "db.transaction.provider_round_closed")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win после рефанда", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    testData.registeredPlayer.getWalletData().getPlayerUUID());

            assertNotNull(threshold, "db.threshold");

            assertAll(
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getPlayerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, testData.betAmount.negate().add(testData.refundEvent.getPayload().getAmount()).compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("Kafka: Проверка поступления сообщения о рефанде в топик wallet.v8.projectionSource", () -> {
            var message = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    testData.refundEvent.getSequence());

            assertNotNull(message, "kafka.message");
            assertTrue(utils.areEquivalent(message, testData.refundEvent), "kafka.message.equivalent_to_nats");
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька после рефанда", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.refundEvent.getSequence());

            assertNotNull(aggregate, "redis.aggregate");

            assertAll(
                    () -> assertEquals(testData.refundEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.aggregate.seq_number"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRefund.compareTo(aggregate.getBalance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, testData.expectedBalanceAfterRefund.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.aggregate.available_balance"),
                    () -> assertTrue(aggregate.getGambling().containsKey(testData.refundEvent.getPayload().getUuid()), "redis.aggregate.gambling_contains_uuid"),
                    () -> assertEquals(0, testData.refundAmount.compareTo(aggregate.getGambling().get(testData.refundEvent.getPayload().getUuid()).getAmount()), "redis.aggregate.gambling_amount"),
                    () -> assertNotNull(aggregate.getGambling().get(testData.refundEvent.getPayload().getUuid()).getCreatedAt(), "redis.aggregate.gambling_created_at")
            );
        });
    }
}