package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий лимит хранения истории гэмблинг-операций турнирных выигрышей
 * в агрегированных данных кошелька в Redis.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что система Wallet корректно обрабатывает большое количество операций турнирных выигрышей
 * и в кэше Redis (в поле {@code Gambling} объекта {@code WalletFullData}, которое является {@link java.util.Map Map})
 * хранится ограниченное количество последних транзакций.
 * Лимит определяется параметром конфигурации {@code app.redis.aggregate.max-gambling.count}.
 * Это важно для оптимизации производительности и управления объемом данных в кэше.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок с начальным нулевым балансом ({@link #initialBalance}).
 *       Баланс будет увеличиваться с каждым турнирным выигрышем.</li>
 *   <li><b>Создание игровой сессии:</b> Для зарегистрированного игрока инициируется новая игровая сессия.</li>
 *   <li><b>Совершение операций турнирного выигрыша:</b> Последовательно через Manager API ({@code /tournament})
 *       совершается количество операций, равное {@code maxGamblingCountInRedis + 1}.
 *       Каждая операция имеет уникальный ID транзакции и сумму {@link #operationAmount}.
 *       ID последней совершенной транзакции сохраняется.
 *       Отслеживается ожидаемый баланс игрока после каждой операции.</li>
 *   <li><b>Получение Sequence Number последней операции:</b> После совершения всех операций, тест ожидает
 *       поступление NATS-события {@code tournament_won_from_gamble} для самой последней транзакции.
 *       Из этого события извлекается {@code sequence number}, который необходим для
 *       запроса консистентных данных из Redis.</li>
 *   <li><b>Запрос данных кошелька из Redis:</b> Используя UUID кошелька и полученный {@code sequence number}
 *       последней транзакции, запрашиваются полные агрегированные данные кошелька
 *       (например, {@code com.uplatform.wallet_tests.api.redis.model.WalletFullData}) из Redis.</li>
 *   <li><b>Проверка содержимого поля {@code Gambling} ({@link java.util.Map Map}) в Redis:</b>
 *     <ul>
 *       <li>Проверяется, что {@link java.util.Map Map} {@code Gambling} в полученных данных содержит ровно
 *           {@code maxGamblingCountInRedis} записей.</li>
 *     </ul>
 *   </li>
 *   <li><b>Дополнительные проверки Redis:</b> Проверяется, что итоговый баланс кошелька в Redis
 *       и {@code lastSeqNumber} соответствуют ожидаемым значениям.</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Позитивные сценарии: /tournament")
@Tag("Gambling") @Tag("Wallet")
@DisplayName("Проверка лимита агрегата Gambling транзакций в Redis для турнирных выигрышей")
class TournamentGamblingHistoryLimitTest extends BaseTest {

    private static final BigDecimal operationAmount = new BigDecimal("5.00");
    private static final BigDecimal initialBalance = BigDecimal.ZERO;

    @Test
    @DisplayName("Проверка вытеснения турнирных выигрышей из кэша Redis")
    void testTournamentHistoryCountLimitInRedis() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final int maxGamblingCountInRedis = 50;

        final int operationsToMake = maxGamblingCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            String lastTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastTournamentEvent;
            BigDecimal currentBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя с нулевым балансом", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialBalance);
            ctx.currentBalance = ctx.registeredPlayer.getWalletData().getBalance();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step(String.format("Manager API: Совершение %d турнирных выигрышей", operationsToMake), () -> {
            for (int i = 0; i < operationsToMake; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == operationsToMake - 1) {
                    ctx.lastTransactionId = transactionId;
                }

                var tournamentRequestBody = TournamentRequestBody.builder()
                        .amount(operationAmount)
                        .playerId(ctx.registeredPlayer.getWalletData().getWalletUUID())
                        .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                        .transactionId(transactionId)
                        .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                        .roundId(UUID.randomUUID().toString())
                        .providerUuid(ctx.gameLaunchData.getDbGameSession().getProviderUuid())
                        .build();

                var currentOperationNumber = i + 1;
                var currentTxId = transactionId;

                step(String.format("Совершение турнирного выигрыша #%d с ID: %s", currentOperationNumber, currentTxId), () -> {
                    var response = managerClient.tournament(
                            casinoId,
                            utils.createSignature(ApiEndpoints.TOURNAMENT, tournamentRequestBody),
                            tournamentRequestBody);

                    ctx.currentBalance = ctx.currentBalance.add(operationAmount);

                    assertAll(String.format("Проверка ответа API для турнирного выигрыша #%d", currentOperationNumber),
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertNotNull(response.getBody(), "manager_api.body_not_null"),
                            () -> assertEquals(currentTxId, response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentBalance.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
                    );
                });
            }
        });

        step(String.format("NATS: Ожидание NATS-события tournament_won_from_gamble для последней операции (ID: %s)", ctx.lastTransactionId), () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.lastTransactionId.equals(payload.getUuid());

            ctx.lastTournamentEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(ctx.lastTournamentEvent, "nats.tournament_won_from_gamble");
            assertEquals(NatsGamblingTransactionOperation.TOURNAMENT, ctx.lastTournamentEvent.getPayload().getOperation(), "nats.payload.operation_type");
        });

        step("Redis(Wallet): Получение и проверка данных кошелька после серии турнирных выигрышей", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.lastTournamentEvent.getSequence());
            var gamblingTransactionsInRedis = aggregate.getGambling();

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(maxGamblingCountInRedis, gamblingTransactionsInRedis.size(), "redis.wallet.gambling.count"),
                    () -> assertEquals(0, ctx.currentBalance.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertEquals((int) ctx.lastTournamentEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number")
            );
        });
    }
}