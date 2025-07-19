package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий лимит хранения истории гэмблинг-операций выигрышей в агрегированных данных кошелька в Redis
 * для различных типов операций (WIN, FREESPIN, JACKPOT).
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что система Wallet корректно обрабатывает большое количество операций выигрышей
 * и в кэше Redis (в поле {@code Gambling} объекта {@code WalletFullData}, которое является {@link java.util.Map Map})
 * хранится ограниченное количество последних транзакций.
 * Лимит определяется параметром конфигурации {@code app.redis.aggregate.max-gambling.count}.
 * Это важно для оптимизации производительности и управления объемом данных в кэше.</p>
 *
 * <p><b>Сценарий теста для каждой операции (WIN, FREESPIN, JACKPOT):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок с начальным нулевым балансом ({@link #initialBalance}).
 *       Баланс будет увеличиваться с каждой операцией выигрыша.</li>
 *   <li><b>Создание игровой сессии:</b> Для зарегистрированного игрока инициируется новая игровая сессия.</li>
 *   <li><b>Совершение операций выигрыша:</b> Последовательно через Manager API ({@code /win})
 *       совершается количество операций выигрыша (заданного типа), равное {@code maxGamblingCountInRedis + 1}.
 *       Каждая операция имеет уникальный ID транзакции и сумму {@link #operationAmount}.
 *       ID последней совершенной транзакции сохраняется.
 *       Отслеживается ожидаемый баланс игрока после каждой операции выигрыша.</li>
 *   <li><b>Получение Sequence Number последней операции:</b> После совершения всех операций, тест ожидает
 *       поступление NATS-события {@code won_from_gamble} для самой последней транзакции.
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
@Feature("/win")
@Suite("Позитивные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
@DisplayName("Проверка лимита агрегата Gambling транзакций в Redis для различных типов операций")
class WinGamblingHistoryLimitTest extends BaseParameterizedTest {

    private static final BigDecimal operationAmount = new BigDecimal("1.00");
    private static final BigDecimal initialBalance = BigDecimal.ZERO;

    static Stream<Arguments> winOperationProvider() {
        return Stream.of(
                arguments(NatsGamblingTransactionOperation.WIN),
                arguments(NatsGamblingTransactionOperation.FREESPIN),
                arguments(NatsGamblingTransactionOperation.JACKPOT)
        );
    }

    @ParameterizedTest(name = "операция выигрыша = {0}")
    @MethodSource("winOperationProvider")
    void testWinGamblingHistoryCountLimitInRedis(NatsGamblingTransactionOperation operationParam) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final int maxGamblingCountInRedis = 50;

        final int operationsToMake = maxGamblingCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            String lastTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastWinEvent;
            BigDecimal currentBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialBalance);
            ctx.currentBalance = ctx.registeredPlayer.getWalletData().getBalance();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии ", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step(String.format("Manager API: Совершение %d операций типа %s", operationsToMake, operationParam), () -> {
            for (int i = 0; i < operationsToMake; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == operationsToMake - 1) {
                    ctx.lastTransactionId = transactionId;
                }

                WinRequestBody winRequestBody = WinRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                        .amount(operationAmount)
                        .transactionId(transactionId)
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(true)
                        .build();

                var currentOperationNumber = i + 1;
                var currentTxId = transactionId;

                step(String.format("Совершение операции  %s #%d с ID: %s", operationParam, currentOperationNumber, currentTxId), () -> {
                    var response = managerClient.win(
                            casinoId,
                            utils.createSignature(ApiEndpoints.WIN, winRequestBody),
                            winRequestBody);

                    ctx.currentBalance = ctx.currentBalance.add(operationAmount);

                    assertAll(String.format("Проверка ответа API для операции %s #%d", operationParam, currentOperationNumber),
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertEquals(currentTxId, response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentBalance.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
                    );
                });
            }
        });

        step(String.format("NATS: Ожидание NATS-события won_from_gamble для последней операции %s (ID: %s)", operationParam, ctx.lastTransactionId), () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.lastTransactionId.equals(payload.getUuid());

            ctx.lastWinEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(ctx.lastWinEvent, "nats.won_from_gamble");
        });

        step(String.format("Redis(Wallet): Получение и проверка данных кошелька для операции %s", operationParam), () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.lastWinEvent.getSequence());
            var gamblingTransactionsInRedis = aggregate.getGambling();

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(maxGamblingCountInRedis, gamblingTransactionsInRedis.size(), "redis.wallet.gambling.count"),
                    () -> assertEquals(0, ctx.currentBalance.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertEquals((int) ctx.lastWinEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number")
            );
        });
    }
}