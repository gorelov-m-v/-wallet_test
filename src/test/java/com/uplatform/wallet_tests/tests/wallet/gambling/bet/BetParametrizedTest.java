package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий функциональность совершения ставок в системе Wallet для азартных игр.
 *
 * <p>Данный параметризованный тест проверяет полный жизненный цикл операции совершения ставки
 * различных типов (BET, TIPS, FREESPIN)
 * и с различными суммами. Тест включает как нулевые суммы, так и ненулевые суммы, динамически
 * генерируемые в допустимых пределах начального баланса игрока. Проверяется распространение
 * события по всем ключевым компонентам системы.</p>
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным состоянием,
 * включая создание нового игрока и игровой сессии, для обеспечения надежности при параллельном выполнении.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>REST API: Совершение ставки через Manager API ({@code /bet}).</li>
 *   <li>Система обмена сообщениями: Передача события {@code betted_from_gamble} через NATS.</li>
 *   <li>База данных (Wallet):
 *     <ul>
 *       <li>Сохранение транзакции в истории ставок казино ({@code gambling_projection_transaction_history}).</li>
 *       <li>Обновление порогов выигрыша игрока для функционала тэгирования ({@code player_threshold_win}).</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Обновление агрегированных данных кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 *   <li>Kafka: Трансляция события в Kafka для сервиса отчетов (топик {@code wallet.v8.projectionSource}).</li>
 * </ul>
 *
 * <p><b>Проверяемые типы ставок ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка.</li>
 *   <li>{@code TIPS} - чаевые.</li>
 *   <li>{@code FREESPIN} - бесплатные вращения.
 * </ul>
 *
 * <p><b>Проверяемые суммы ставок:</b></p>
 * <ul>
 *   <li>Динамически генерируемые ненулевые значения (в пределах {@link #initialAdjustmentAmount}).</li>
 *   <li>Нулевые значения ({@code 0.00}).</li>
 *   <li>Значения равные размеру баланса игрока ({@link #initialAdjustmentAmount}).</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/bet")
@Suite("Позитивные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class BetParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private final String expectedCurrencyRates = "1";

    static Stream<Arguments> betAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        initialAdjustmentAmount,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        initialAdjustmentAmount,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        initialAdjustmentAmount,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                )
        );
    }

    @ParameterizedTest(name = "тип = {2} и сумма = {0}")
    @MethodSource("betAmountProvider")
    @DisplayName("Совершение ставки:")
    void test(
            BigDecimal amountParam,
            NatsGamblingTransactionOperation operationParam,
            NatsGamblingTransactionType transactionTypeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            BigDecimal expectedBalance;
        }
        final TestContext ctx = new TestContext();
        ctx.expectedBalance = BigDecimal.ZERO.add(initialAdjustmentAmount).subtract(amountParam);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение ставки", () -> {
            var request = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(amountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();
            ctx.betRequestBody = request;

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(request.getTransactionId(), response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
            );
        });

        step("NATS: Проверка поступления события betted_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.betRequestBody.getTransactionId().equals(payload.getUuid());

            ctx.betEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            var betRequest = ctx.betRequestBody;
            var betEvent = ctx.betEvent.getPayload();
            var session = ctx.gameLaunchData.getDbGameSession();
            var player = ctx.registeredPlayer.getWalletData();
            assertAll("Проверка основных полей NATS payload",
                    () -> assertEquals(betRequest.getTransactionId(), betEvent.getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(new UUID(0L, 0L).toString(), betEvent.getBetUuid(), "nats.payload.bet_uuid"),
                    () -> assertEquals(session.getGameSessionUuid(), betEvent.getGameSessionUuid(), "nats.payload.game_session_uuid"),
                    () -> assertEquals(betRequest.getRoundId(), betEvent.getProviderRoundId(), "nats.payload.provider_round_id"),
                    () -> assertEquals(player.getCurrency(), betEvent.getCurrency(), "nats.payload.currency"),
                    () -> assertEquals(0, amountParam.negate().compareTo(betEvent.getAmount()), "nats.payload.amount"),
                    () -> assertEquals(transactionTypeParam, betEvent.getType(), "nats.payload.type"),
                    () -> assertFalse(betEvent.isProviderRoundClosed(), "nats.payload.provider_round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, betEvent.getMessage(), "nats.payload.message"),
                    () -> assertNotNull(betEvent.getCreatedAt(), "nats.payload.created_at"),
                    () -> assertEquals(NatsTransactionDirection.WITHDRAW, betEvent.getDirection(), "nats.payload.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.BET, betEvent.getOperation(), "nats.payload.operation"),
                    () -> assertEquals(platformNodeId, betEvent.getNodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals(session.getGameUuid(), betEvent.getGameUuid(), "nats.payload.game_uuid"),
                    () -> assertEquals(session.getProviderUuid(), betEvent.getProviderUuid(), "nats.payload.provider_uuid"),
                    () -> assertTrue(betEvent.getWageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info")
            );

            var conversionInfo = betEvent.getCurrencyConversionInfo();
            var currencyRates = conversionInfo.getCurrencyRates().get(0);
            assertAll("Проверка полей внутри currency_conversion_info NATS payload",
                    () -> assertEquals(0, amountParam.negate().compareTo(conversionInfo.getGameAmount()), "currency_conversion_info.game_amount"),
                    () -> assertFalse(conversionInfo.getGameCurrency().isEmpty(), "currency_conversion_info.game_currency"),
                    () -> assertEquals(player.getCurrency(), currencyRates.getBaseCurrency(), "currency_conversion_info.currency_rates.base_currency"),
                    () -> assertEquals(player.getCurrency(), currencyRates.getQuoteCurrency(), "currency_conversion_info.currency_rates.quote_currency"),
                    () -> assertEquals(expectedCurrencyRates, currencyRates.getValue(), "currency_conversion_info.currency_rates.value"),
                    () -> assertNotNull(currencyRates.getUpdatedAt(), "currency_conversion_info.currency_rates.updated_at")
            );
        });

        step("DB Wallet: Проверка записи истории ставок в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.findTransactionByUuidOrFail(ctx.betRequestBody.getTransactionId());
            var payload = ctx.betEvent.getPayload();
            assertAll("Проверка полей gambling_projection_transaction_history",
                    () -> assertEquals(payload.getUuid(), transaction.getUuid(), "db.gpth.uuid"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getPlayerUUID(), transaction.getPlayerUuid(), "db.gpth.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.gpth.date"),
                    () -> assertEquals(payload.getType(), transaction.getType(), "db.gpth.type"),
                    () -> assertEquals(payload.getOperation(), transaction.getOperation(), "db.gpth.operation"),
                    () -> assertEquals(payload.getGameUuid(), transaction.getGameUuid(), "db.gpth.game_uuid"),
                    () -> assertEquals(payload.getGameSessionUuid(), transaction.getGameSessionUuid(), "db.gpth.game_session_uuid"),
                    () -> assertEquals(payload.getCurrency(), transaction.getCurrency(), "db.gpth.currency"),
                    () -> assertEquals(0, amountParam.negate().compareTo(transaction.getAmount()), "db.gpth.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.gpth.created_at"),
                    () -> assertEquals(ctx.betEvent.getSequence(), transaction.getSeqnumber(), "db.gpth.seqnumber"),
                    () -> assertEquals(payload.isProviderRoundClosed(), transaction.isProviderRoundClosed(), "db.gpth.provider_round_closed"),
                    () -> assertEquals(payload.getBetUuid(), transaction.getBetUuid(), "db.gpth.bet_uuid")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var playerUuid = ctx.registeredPlayer.getWalletData().getPlayerUUID();
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(playerUuid);
            assertAll("Проверка полей player_threshold_win",
                    () -> assertEquals(playerUuid, threshold.getPlayerUuid(), "db.ptw.player_uuid"),
                    () -> assertEquals(0, amountParam.negate().compareTo(threshold.getAmount()), "db.ptw.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.ptw.updated_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var walletUuid = ctx.registeredPlayer.getWalletData().getWalletUUID();
            int sequence = (int) ctx.betEvent.getSequence();
            var transactionUuid = ctx.betEvent.getPayload().getUuid();

            var aggregate = redisClient.getWalletDataWithSeqCheck(walletUuid, sequence);

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(sequence, aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.wallet.availableWithdrawalBalance"),
                    () -> assertTrue(aggregate.getGambling().containsKey(transactionUuid), "redis.wallet.gambling.containsKey"),
                    () -> assertEquals(0, amountParam.negate().compareTo(aggregate.getGambling().get(transactionUuid).getAmount()), "redis.wallet.gambling.amount"),
                    () -> assertNotNull(aggregate.getGambling().get(transactionUuid).getCreatedAt(), "redis.wallet.gambling.createdAt")
            );
        });

        step("Kafka: Проверка поступления сообщения betted_from_gamble в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.betEvent.getSequence());

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.betEvent), "wallet.v8.projectionSource");
        });
    }
}