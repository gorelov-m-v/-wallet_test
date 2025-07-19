package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный параметризованный тест, проверяющий API ответ при попытке совершить дублирующую ставку
 * на транзакцию, которая была вытеснена из кеша Redis. Тест покрывает различные типы операций
 * (BET, TIPS, FREESPIN) и суммы (включая нулевую).
 * Ожидается, что система найдет транзакцию в основном хранилище и вернет идемпотентный ответ.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager корректно обрабатывает запрос на дублирующую ставку для каждого типа операции и суммы,
 * даже если информация об оригинальной ставке отсутствует в "горячем" кеше Redis.
 * Тест ожидает, что система найдет транзакцию в основном хранилище и вернет успешный ответ {@link HttpStatus#OK}
 * с телом {@link GamblingResponseBody}, содержащим тот же {@code transactionId} и нулевой баланс.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации типа операции и суммы):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока и создание сессии:</b> Подготавливается игрок и игровая сессия.</li>
 *   <li><b>Совершение вытесняющих ставок:</b> Через API совершается {@code maxGamblingCountInRedis + 1}
 *       запросов на ставку, чтобы гарантированно вытеснить одну транзакцию из кеша Redis.</li>
 *   <li><b>Получение Sequence последней ставки:</b> Через NATS ожидается событие от последней ставки
 *       для получения ее {@code sequence number}.</li>
 *   <li><b>Определение вытесненной транзакции:</b> Запрашиваются данные из Redis для агрегата кошелька.
 *       Сравнивая список всех сделанных транзакций со списком в Redis, определяется ID транзакции,
 *       которая была вытеснена.</li>
 *   <li><b>Попытка дублирования вытесненной транзакции:</b> Через API отправляется новый запрос на ставку,
 *       используя тот же {@code transactionId}, что и у вытесненной транзакции.</li>
 *   <li><b>Проверка ответа API:</b> Ожидается, что API вернет успешный ответ со статусом {@link HttpStatus#OK}.
 *       Тело ответа ({@link GamblingResponseBody}) должно содержать {@code transactionId} из первого запроса и баланс,
 *       равный {@link BigDecimal#ZERO}.</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class DuplicateDisplacedBetParametrizedTest extends BaseParameterizedTest {


    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("100.00");
    private static final BigDecimal defaultBetAmount = new BigDecimal("1.00");

    static Stream<Arguments> betOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.BET, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO)
        );
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("betOperationAndAmountProvider")
    @DisplayName("Дублирование ставки, вытесненной из кеша")
    void testDuplicateDisplacedBetReturnsIdempotentResponse(NatsGamblingTransactionOperation operationParam, BigDecimal betAmountParam) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final int maxGamblingCountInRedis = 50;

        final int betsToMakeToDisplace = maxGamblingCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<BetRequestBody> allMadeBetRequests = new ArrayList<>();
            String lastMadeBetTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastBetNatsEvent;
            BetRequestBody displacedBetRequestToDuplicate;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение ставок для вытеснения (тип: " + operationParam + ", сумма: " + betAmountParam + ")", () -> {
            for (int i = 0; i < betsToMakeToDisplace; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == betsToMakeToDisplace - 1) {
                    ctx.lastMadeBetTransactionId = transactionId;
                }
                var betRequestBody = BetRequestBody.builder()
                        .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                        .amount(betAmountParam)
                        .transactionId(transactionId)
                        .type(operationParam)
                        .roundId(UUID.randomUUID().toString())
                        .roundClosed(false)
                        .build();
                ctx.allMadeBetRequests.add(betRequestBody);

                var currentBetNumber = i + 1;
                step("Совершение ставки #" + currentBetNumber + " (ID: " + transactionId + ")", () -> {
                    var response = managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, betRequestBody),
                            betRequestBody);
                    assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");
                });
            }
        });

        step("NATS: Ожидание NATS-события betted_from_gamble для последней ставки", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());
            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.lastMadeBetTransactionId.equals(payload.getUuid());

            ctx.lastBetNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(ctx.lastBetNatsEvent, "nats.betted_from_gamble");
        });

        step("Redis: Определение вытесненной ставки", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.lastBetNatsEvent.getSequence());
            var transactionIdsCurrentlyInRedis = aggregate.getGambling().keySet();

            var allMadeTransactionIds = ctx.allMadeBetRequests.stream()
                    .map(BetRequestBody::getTransactionId)
                    .collect(Collectors.toCollection(HashSet::new));

            allMadeTransactionIds.removeAll(transactionIdsCurrentlyInRedis);
            assertEquals(1, allMadeTransactionIds.size(), "redis.displaced_transaction.count");
            var displacedTxId = allMadeTransactionIds.iterator().next();

            ctx.displacedBetRequestToDuplicate = ctx.allMadeBetRequests.stream()
                    .filter(betReq -> betReq.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));
        });

        step("Manager API: Попытка дублирования вытесненной ставки", () -> {
            var duplicateResponse = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.displacedBetRequestToDuplicate),
                    ctx.displacedBetRequestToDuplicate
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("manager_api.duplicate_displaced_bet.response",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(responseBody, "manager_api.response_body"),
                    () -> assertEquals(ctx.displacedBetRequestToDuplicate.getTransactionId(), responseBody.getTransactionId(), "manager_api.transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.getBalance()), "manager_api.balance")
            );
        });
    }
}