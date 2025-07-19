package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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
 * Интеграционный тест, проверяющий функциональность получения выигрышей в системе Wallet для азартных игр.
 *
 * <p>Данный параметризованный тест проверяет полный жизненный цикл операции получения выигрыша
 * различных типов (WIN, FREESPIN, JACKPOT) и с различными суммами. Тест включает как нулевые суммы,
 * так и ненулевые значения. Проверяется распространение события по всем ключевым компонентам системы.</p>
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным состоянием,
 * включая создание нового игрока и игровой сессии, для обеспечения надежности при параллельном выполнении.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>REST API: Получение выигрыша через Manager API ({@code /win}).</li>
 *   <li>Система обмена сообщениями: Передача события {@code won_from_gamble} через NATS.</li>
 *   <li>База данных (Wallet):
 *     <ul>
 *       <li>Сохранение транзакции в истории выигрышей казино ({@code gambling_projection_transaction_history}).</li>
 *       <li>Обновление порогов выигрыша игрока для функционала тегирования ({@code player_threshold_win}).</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Обновление агрегированных данных кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 *   <li>Kafka: Трансляция события в Kafka для сервиса отчетов (топик {@code wallet.v8.projectionSource}).</li>
 * </ul>
 *
 * <p><b>Проверяемые типы ставок ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code WIN} - обычный выигрыш.</li>
 *   <li>{@code FREESPIN} - выигрыш от бесплатных вращений.</li>
 *   <li>{@code JACKPOT} - выигрыш джекпота.</li>
 * </ul>
 *
 * <p><b>Проверяемые суммы выигрышей:</b></p>
 * <ul>
 *   <li>Динамически генерируемые ненулевые значения (в пределах {@link #initialAdjustmentAmount}).</li>
 *   <li>Нулевые значения ({@code 0.00}).</li>
 * </ul>
 */
@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/win")
@Suite("Позитивные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class WinParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final String expectedCurrencyRates = "1";

    static Stream<Arguments> winAmountProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_JACKPOT
                ),
                Arguments.of(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_JACKPOT
                )
        );
    }

    /**
     * @param betAmountParam Сумма ставки, сгенерированная для текущего теста.
     * @param winAmountParam Сумма выигрыша, которая может быть как ненулевой (случайно сгенерированной),
     *                      так и нулевой в зависимости от сценария теста.
     * @param operationParam Тип операции выигрыша (WIN, FREESPIN или JACKPOT) для запроса и проверки.
     * @param transactionTypeParam Ожидаемый тип транзакции в событии NATS для соответствующей проверки.
     */
    @MethodSource("winAmountProvider")
    @ParameterizedTest(name = "тип = {2} и сумма = {1}")
    @DisplayName("Получение выигрыша:")
    void test(
            BigDecimal betAmountParam,
            BigDecimal winAmountParam,
            NatsGamblingTransactionOperation operationParam,
            NatsGamblingTransactionType transactionTypeParam) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            WinRequestBody winRequestBody;
            NatsMessage<NatsGamblingEventPayload> winEvent;
            BigDecimal expectedBalanceAfterBet;
            BigDecimal expectedBalanceAfterWin;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalanceAfterBet = BigDecimal.ZERO.add(initialAdjustmentAmount).subtract(betAmountParam);
        ctx.expectedBalanceAfterWin = ctx.expectedBalanceAfterBet.add(winAmountParam);

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
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();
            ctx.betRequestBody = request;

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, request),
                    request);

            assertAll("Проверка статус-кода и тела ответа ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.bet.body_not_null"),
                    () -> assertEquals(request.getTransactionId(), response.getBody().getTransactionId(), "manager_api.bet.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterBet.compareTo(response.getBody().getBalance()), "manager_api.bet.body.balance")
            );
        });

        step("Manager API: Получение выигрыша", () -> {
            ctx.winRequestBody = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.winRequestBody),
                    ctx.winRequestBody);

            assertAll("Проверка статус-кода и тела ответа выигрыша",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.win.body_not_null"),
                    () -> assertEquals(ctx.winRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.win.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(response.getBody().getBalance()), "manager_api.win.body.balance")
            );
        });

        step("NATS: Проверка поступления события won_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.winRequestBody.getTransactionId().equals(payload.getUuid());

            ctx.winEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            var winRequest = ctx.winRequestBody;
            var winEventPayload = ctx.winEvent.getPayload();
            var session = ctx.gameLaunchData.getDbGameSession();
            var player = ctx.registeredPlayer.getWalletData();

            assertAll("Проверка основных полей NATS payload",
                    () -> assertEquals(winRequest.getTransactionId(), winEventPayload.getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(new UUID(0L, 0L).toString(), winEventPayload.getBetUuid(), "nats.payload.bet_uuid"),
                    () -> assertEquals(session.getGameSessionUuid(), winEventPayload.getGameSessionUuid(), "nats.payload.game_session_uuid"),
                    () -> assertEquals(winRequest.getRoundId(), winEventPayload.getProviderRoundId(), "nats.payload.provider_round_id"),
                    () -> assertEquals(player.getCurrency(), winEventPayload.getCurrency(), "nats.payload.currency"),
                    () -> assertEquals(0, winAmountParam.compareTo(winEventPayload.getAmount()), "nats.payload.amount"),
                    () -> assertEquals(transactionTypeParam, winEventPayload.getType(), "nats.payload.type"),
                    () -> assertTrue(winEventPayload.isProviderRoundClosed(), "nats.payload.provider_round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, winEventPayload.getMessage(), "nats.payload.message"),
                    () -> assertNotNull(winEventPayload.getCreatedAt(), "nats.payload.created_at"),
                    () -> assertEquals(NatsTransactionDirection.DEPOSIT, winEventPayload.getDirection(), "nats.payload.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.WIN, winEventPayload.getOperation(), "nats.payload.operation"),
                    () -> assertEquals(platformNodeId, winEventPayload.getNodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals(session.getGameUuid(), winEventPayload.getGameUuid(), "nats.payload.game_uuid"),
                    () -> assertEquals(session.getProviderUuid(), winEventPayload.getProviderUuid(), "nats.payload.provider_uuid"),
                    () -> assertTrue(winEventPayload.getWageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info")
            );

            var conversionInfo = winEventPayload.getCurrencyConversionInfo();
            var currencyRates = conversionInfo.getCurrencyRates().get(0);

            assertAll("Проверка полей внутри currency_conversion_info NATS payload",
                    () -> assertEquals(0, winAmountParam.compareTo(conversionInfo.getGameAmount()), "currency_conversion_info.game_amount"),
                    () -> assertEquals(player.getCurrency(), conversionInfo.getGameCurrency(), "currency_conversion_info.game_currency"),
                    () -> assertEquals(player.getCurrency(), currencyRates.getBaseCurrency(), "currency_conversion_info.currency_rates.base_currency"),
                    () -> assertEquals(player.getCurrency(), currencyRates.getQuoteCurrency(), "currency_conversion_info.currency_rates.quote_currency"),
                    () -> assertEquals(expectedCurrencyRates, currencyRates.getValue(), "currency_conversion_info.currency_rates.value"),
                    () -> assertNotNull(currencyRates.getUpdatedAt(), "currency_conversion_info.currency_rates.updated_at")
            );
        });

        step("DB Wallet: Проверка записи истории ставок в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.findTransactionByUuidOrFail(ctx.winRequestBody.getTransactionId());
            var payload = ctx.winEvent.getPayload();

            assertAll("Проверка полей gambling_projection_transaction_history",
                    () -> assertEquals(payload.getUuid(), transaction.getUuid(), "db.gpth.uuid"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getPlayerUUID(), transaction.getPlayerUuid(), "db.gpth.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.gpth.date"),
                    () -> assertEquals(payload.getType(), transaction.getType(), "db.gpth.type"),
                    () -> assertEquals(payload.getOperation(), transaction.getOperation(), "db.gpth.operation"),
                    () -> assertEquals(payload.getGameUuid(), transaction.getGameUuid(), "db.gpth.game_uuid"),
                    () -> assertEquals(payload.getGameSessionUuid(), transaction.getGameSessionUuid(), "db.gpth.game_session_uuid"),
                    () -> assertEquals(payload.getCurrency(), transaction.getCurrency(), "db.gpth.currency"),
                    () -> assertEquals(0, winAmountParam.compareTo(transaction.getAmount()), "db.gpth.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.gpth.created_at"),
                    () -> assertEquals(ctx.winEvent.getSequence(), transaction.getSeqnumber(), "db.gpth.seqnumber"),
                    () -> assertEquals(payload.isProviderRoundClosed(), transaction.isProviderRoundClosed(), "db.gpth.provider_round_closed"),
                    () -> assertEquals(payload.getBetUuid(), transaction.getBetUuid(), "db.gpth.bet_uuid")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var playerUuid = ctx.registeredPlayer.getWalletData().getPlayerUUID();
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(playerUuid);
            BigDecimal expectedThresholdAmount = winAmountParam.subtract(betAmountParam);

            assertAll("Проверка полей player_threshold_win",
                    () -> assertEquals(playerUuid, threshold.getPlayerUuid(), "db.ptw.player_uuid"),
                    () -> assertEquals(0, expectedThresholdAmount.compareTo(threshold.getAmount()), "db.ptw.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.ptw.updated_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var walletUuid = ctx.registeredPlayer.getWalletData().getWalletUUID();
            int sequence = (int) ctx.winEvent.getSequence();
            var transactionUuid = ctx.winEvent.getPayload().getUuid();

            var aggregate = redisClient.getWalletDataWithSeqCheck(walletUuid, sequence);

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(sequence, aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterWin.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.wallet.availableWithdrawalBalance"),
                    () -> assertTrue(aggregate.getGambling().containsKey(transactionUuid), "redis.wallet.gambling.containsKey"),
                    () -> assertEquals(0, winAmountParam.compareTo(aggregate.getGambling().get(transactionUuid).getAmount()), "redis.wallet.gambling.amount"),
                    () -> assertNotNull(aggregate.getGambling().get(transactionUuid).getCreatedAt(), "redis.wallet.gambling.createdAt")
            );
        });

        step("Kafka: Проверка поступления сообщения won_from_gamble в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.winEvent.getSequence());

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.winEvent), "wallet.v8.projectionSource");
        });
    }
}