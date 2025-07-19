package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
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

/**
 * Интеграционный тест, проверяющий лимит хранения истории гэмблинг-операций в агрегированных данных кошелька в Redis
 * для различных типов операций (BET, TIPS, FREESPIN).
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что система Wallet корректно обрабатывает большое количество операций
 * и в кэше Redis (в поле {@code Gambling} объекта {@code WalletFullData}, которое является {@link java.util.Map Map})
 * хранится ограниченное количество последних транзакций.
 * Лимит определяется параметром конфигурации {@code app.redis.aggregate.max-gambling.count}.
 * Это важно для оптимизации производительности и управления объемом данных в кэше.</p>
 *
 * <p><b>Сценарий теста для каждой операции (BET, TIPS, FREESPIN):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок с начальным балансом,
 *       динамически рассчитанным так, чтобы быть достаточным для совершения {@code maxGamblingCountInRedis + 1} операций
 *       (каждая на сумму {@link #operationAmount}).</li>
 *   <li><b>Создание игровой сессии:</b> Для зарегистрированного игрока инициируется новая игровая сессия.</li>
 *   <li><b>Совершение операций:</b> Последовательно через Manager API ({@code /bet})
 *       совершается количество операций (заданного типа), равное {@code maxGamblingCountInRedis + 1}.
 *       Каждая операция имеет уникальный ID транзакции и сумму {@link #operationAmount}.
 *       ID последней совершенной транзакции сохраняется.
 *       Отслеживается ожидаемый баланс игрока после каждой операции.</li>
 *   <li><b>Получение Sequence Number последней операции:</b> После совершения всех операций, тест ожидает
 *       поступление NATS-события {@code betted_from_gamble} для самой последней транзакции.
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
@Feature("/bet")
@Suite("Позитивные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
@DisplayName("Проверка лимита агрегата Gambling транзакций в Redis для различных типов операций")
class BetGamblingHistoryLimitTest extends BaseParameterizedTest {

    private static final BigDecimal operationAmount = new BigDecimal("1.00");

    static Stream<Arguments> gamblingOperationProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET),
                Arguments.of(NatsGamblingTransactionOperation.TIPS),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN)
        );
    }

    @ParameterizedTest(name = "операция = {0}")
    @MethodSource("gamblingOperationProvider")
    void testGamblingHistoryCountLimitInRedis(NatsGamblingTransactionOperation operationParam) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final int maxGamblingCountInRedis = 50;

        final int operationsToMake = maxGamblingCountInRedis + 1;
        final BigDecimal dynamicInitialAdjustmentAmount = operationAmount
                .multiply(new BigDecimal(operationsToMake))
                .add(new BigDecimal("10.00"));

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            String lastTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastBetEvent;
            BigDecimal currentBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(dynamicInitialAdjustmentAmount);
            ctx.currentBalance = ctx.registeredPlayer.getWalletData().getBalance();

            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии для операции", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);

            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step(String.format("Manager API: Совершение %d операций типа %s", operationsToMake, operationParam), () -> {
            for (int i = 0; i < operationsToMake; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == operationsToMake - 1) {
                    ctx.lastTransactionId = transactionId;
                }

                var betRequestBody = BetRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                        .amount(operationAmount)
                        .transactionId(transactionId)
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(false)
                        .build();

                var currentOperationNumber = i + 1;
                var currentTxId = transactionId;

                step(String.format("Совершение операции %s #%d с ID: %s", operationParam, currentOperationNumber, currentTxId), () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);

                    ctx.currentBalance = ctx.currentBalance.subtract(operationAmount);

                    assertAll(String.format("Проверка ответа API для операции %s #%d", operationParam, currentOperationNumber),
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertEquals(currentTxId, response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                            () -> assertEquals(0, ctx.currentBalance.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
                    );
                });
            }
        });

        step(String.format("NATS: Ожидание NATS-события betted_from_gamble для последней операции %s (ID: %s)", operationParam, ctx.lastTransactionId), () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.lastTransactionId.equals(payload.getUuid());

            ctx.lastBetEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(ctx.lastBetEvent, "nats.betted_from_gamble");
        });

        step(String.format("Redis(Wallet): Получение и проверка данных кошелька для операции %s", operationParam), () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.lastBetEvent.getSequence());
            var gamblingTransactionsInRedis = aggregate.getGambling();

            assertAll("Проверка данных в Redis",
                    () -> assertEquals(maxGamblingCountInRedis, gamblingTransactionsInRedis.size(), "redis.wallet.gambling.count"),
                    () -> assertEquals(0, ctx.currentBalance.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertEquals((int) ctx.lastBetEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number")
            );
        });
    }
}