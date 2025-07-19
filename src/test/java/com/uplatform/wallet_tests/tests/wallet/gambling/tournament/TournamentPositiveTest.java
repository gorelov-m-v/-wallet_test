package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("/tournament")
@Tag("Wallet")
@Suite("Позитивные сценарии: /tournament")
@Tag("Gambling") @Tag("Wallet")
class TournamentPositiveTest extends BaseTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal tournamentAmount = generateBigDecimalAmount(initialAdjustmentAmount);
    private final String expectedCurrencyRates = "1";

    @Test
    @DisplayName("Получение выигрыша в турнире игроком в игровой сессии")
    void shouldAwardTournamentWinAndVerify() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequestBody;
            NatsMessage<NatsGamblingEventPayload> tournamentEvent;
            BigDecimal expectedBalanceAfterTournament;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalanceAfterTournament = BigDecimal.ZERO
                .add(initialAdjustmentAmount)
                .add(tournamentAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии и проверка в БД", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            ctx.tournamentRequestBody = TournamentRequestBody.builder()
                    .amount(tournamentAmount)
                    .playerId(ctx.registeredPlayer.getWalletData().getWalletUUID())
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .transactionId(UUID.randomUUID().toString())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(UUID.randomUUID().toString())
                    .providerUuid(ctx.gameLaunchData.getDbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, ctx.tournamentRequestBody),
                    ctx.tournamentRequestBody);

            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.tournament.body_not_null"),
                    () -> assertEquals(ctx.tournamentRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.tournament.transaction_id"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(response.getBody().getBalance()), "manager_api.tournament.balance")
            );
        });

        step("NATS: Проверка поступления события tournament_won_from_gamble", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.tournamentRequestBody.getTransactionId().equals(payload.getUuid());

            ctx.tournamentEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertAll(
                    () -> assertEquals(ctx.tournamentRequestBody.getTransactionId(), ctx.tournamentEvent.getPayload().getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(new UUID(0L, 0L).toString(), ctx.tournamentEvent.getPayload().getBetUuid(), "nats.payload.bet_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getSessionToken(), ctx.tournamentEvent.getPayload().getGameSessionUuid(), "nats.payload.game_session_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getRoundId(), ctx.tournamentEvent.getPayload().getProviderRoundId(), "nats.payload.provider_round_id"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), ctx.tournamentEvent.getPayload().getCurrency(), "nats.payload.currency"),
                    () -> assertEquals(0, tournamentAmount.compareTo(ctx.tournamentEvent.getPayload().getAmount()), "nats.payload.amount"),
                    () -> assertEquals(NatsGamblingTransactionType.TYPE_TOURNAMENT, ctx.tournamentEvent.getPayload().getType(), "nats.payload.type"),
                    () -> assertFalse(ctx.tournamentEvent.getPayload().isProviderRoundClosed(), "nats.payload.provider_round_closed"),
                    () -> assertEquals(NatsMessageName.WALLET_GAME_TRANSACTION, ctx.tournamentEvent.getPayload().getMessage(), "nats.payload.message"),
                    () -> assertNotNull(ctx.tournamentEvent.getPayload().getCreatedAt(), "nats.payload.created_at"),
                    () -> assertEquals(NatsTransactionDirection.DEPOSIT, ctx.tournamentEvent.getPayload().getDirection(), "nats.payload.direction"),
                    () -> assertEquals(NatsGamblingTransactionOperation.TOURNAMENT, ctx.tournamentEvent.getPayload().getOperation(), "nats.payload.operation"),
                    () -> assertEquals(platformNodeId, ctx.tournamentEvent.getPayload().getNodeUuid(), "nats.payload.node_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getGameUuid(), ctx.tournamentEvent.getPayload().getGameUuid(), "nats.payload.game_uuid"),
                    () -> assertEquals(ctx.tournamentRequestBody.getProviderUuid(), ctx.tournamentEvent.getPayload().getProviderUuid(), "nats.payload.provider_uuid"),
                    () -> assertTrue(ctx.tournamentEvent.getPayload().getWageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info"),
                    () -> assertEquals(0, tournamentAmount.compareTo(ctx.tournamentEvent.getPayload().getCurrencyConversionInfo().getGameAmount()), "nats.payload.currency_conversion.game_amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), ctx.tournamentEvent.getPayload().getCurrencyConversionInfo().getGameCurrency(), "nats.payload.currency_conversion.game_currency"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), ctx.tournamentEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getBaseCurrency(), "nats.payload.currency_conversion.base_currency"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), ctx.tournamentEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getQuoteCurrency(), "nats.payload.currency_conversion.quote_currency"),
                    () -> assertEquals(expectedCurrencyRates, ctx.tournamentEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getValue(), "nats.payload.currency_conversion.rate_value"),
                    () -> assertNotNull(ctx.tournamentEvent.getPayload().getCurrencyConversionInfo().getCurrencyRates().get(0).getUpdatedAt(), "nats.payload.currency_conversion.updated_at")
            );
        });

        step("DB Wallet: Проверка записи истории ставок в gambling_projection_transaction_history", () -> {
            var transaction = walletDatabaseClient.
                    findTransactionByUuidOrFail(ctx.tournamentRequestBody.getTransactionId());

            assertAll(
                    () -> assertEquals(ctx.tournamentEvent.getPayload().getUuid(), transaction.getUuid(), "db.transaction.uuid"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getPlayerUUID(), transaction.getPlayerUuid(), "db.transaction.player_uuid"),
                    () -> assertNotNull(transaction.getDate(), "db.transaction.date"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().getType(), transaction.getType(), "db.transaction.type"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().getOperation(), transaction.getOperation(), "db.transaction.operation"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().getGameUuid(), transaction.getGameUuid(), "db.transaction.game_uuid"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().getGameSessionUuid(), transaction.getGameSessionUuid(), "db.transaction.game_session_uuid"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().getCurrency(), transaction.getCurrency(), "db.transaction.currency"),
                    () -> assertEquals(0, ctx.tournamentEvent.getPayload().getAmount().compareTo(transaction.getAmount()), "db.transaction.amount"),
                    () -> assertNotNull(transaction.getCreatedAt(), "db.transaction.created_at"),
                    () -> assertEquals(ctx.tournamentEvent.getSequence(), transaction.getSeqnumber(), "db.transaction.seq_number"),
                    () -> assertEquals(ctx.tournamentEvent.getPayload().isProviderRoundClosed(), transaction.isProviderRoundClosed(), "db.transaction.provider_round_closed")
            );
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID());

            assertAll(
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getPlayerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, BigDecimal.ZERO.add(tournamentAmount).compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька после турнирного выигрыша", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(), (int) ctx.tournamentEvent.getSequence());

            assertAll(
                    () -> assertEquals(ctx.tournamentEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(aggregate.getBalance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, ctx.expectedBalanceAfterTournament.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertTrue(aggregate.getGambling().containsKey(ctx.tournamentEvent.getPayload().getUuid()), "redis.aggregate.gambling.contains"),
                    () -> assertEquals(0, tournamentAmount.compareTo(aggregate.getGambling().get(ctx.tournamentEvent.getPayload().getUuid()).getAmount()), "redis.aggregate.gambling.amount"),
                    () -> assertNotNull(aggregate.getGambling().get(ctx.tournamentEvent.getPayload().getUuid()).getCreatedAt(), "redis.aggregate.gambling.created_at")
            );
        });

        step("Kafka: Проверка поступления сообщения турнира в топик wallet.projectionSource", () -> {
            var message = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.tournamentEvent.getSequence());

            assertTrue(utils.areEquivalent(message, ctx.tournamentEvent), "kafka.payload");
        });
    }
}