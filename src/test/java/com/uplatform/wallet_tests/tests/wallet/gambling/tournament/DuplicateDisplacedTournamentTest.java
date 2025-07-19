package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий API ответ при попытке совершить дублирующее турнирное начисление
 * на транзакцию, которая была вытеснена из кеша Redis (агрегата кошелька).
 * Определение вытесненного турнирного начисления происходит динамически.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager корректно обрабатывает запрос на дублирующее турнирное начисление,
 * если информация об оригинальном начислении отсутствует в "горячем" кеше Redis,
 * но должна быть найдена в основном хранилище. Тест ожидает, что система вернет
 * идемпотентный ответ со статусом {@link HttpStatus#OK}, подтверждая корректную
 * обработку дублирующего запроса.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок с начальным балансом.</li>
 *   <li><b>Создание игровой сессии:</b> Инициируется игровая сессия для зарегистрированного игрока.</li>
 *   <li><b>Совершение вытесняющих турнирных начислений:</b> Через API Manager совершается {@code maxGamblingCountInRedis + 1}
 *       уникальных турнирных начислений. Параметры первого начисления сохраняются (неявно, для последующего определения).</li>
 *   <li><b>Получение Sequence последнего турнирного начисления:</b> Через NATS ожидается событие {@code tournament_won_from_gamble}
 *       от последнего сделанного начисления для получения его {@code sequence number}.</li>
 *   <li><b>Определение вытесненного турнирного начисления:</b> Запрашиваются данные из Redis. Сравнивается список ID всех
 *       сделанных турнирных начислений с ID, находящимися в кеше Redis. Определяется ID начисления, которое было вытеснено.</li>
 *   <li><b>Попытка дублирования вытесненного турнирного начисления:</b> Через API отправляется новый запрос {@code /tournament}
 *       с тем же {@code transactionId}, что и у ранее определенного вытесненного турнирного начисления.</li>
 *   <li><b>Проверка ответа API:</b> Ожидается успешный ответ со статусом {@link HttpStatus#OK},
 *       содержащий {@code transactionId} дублируемого запроса и нулевой баланс.</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Негативные сценарии: /tournament")
@Tag("Gambling") @Tag("Wallet")
class DuplicateDisplacedTournamentTest extends BaseTest {


    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("1000.00");
    private static final BigDecimal singleTournamentAmount = new BigDecimal("10.00");

    @Test
    @DisplayName("Дублирование турнирного начисления, вытесненного из кеша (ожидается ошибка валидации)")
    void testDuplicateDisplacedTournamentExpectingValidationError()  {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();
        final int maxGamblingCountInRedis = 50;

        final int tournamentsToMakeToDisplace = maxGamblingCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            List<TournamentRequestBody> allMadeTournamentRequests = new ArrayList<>();
            String lastMadeTournamentTransactionId;
            NatsMessage<NatsGamblingEventPayload> lastTournamentNatsEvent;
            TournamentRequestBody displacedTournamentRequestToDuplicate;
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

        step("Manager API: Совершение турнирных начислений для вытеснения", () -> {
            for (int i = 0; i < tournamentsToMakeToDisplace; i++) {
                var transactionId = UUID.randomUUID().toString();
                if (i == tournamentsToMakeToDisplace - 1) {
                    ctx.lastMadeTournamentTransactionId = transactionId;
                }
                var tournamentRequestBody = TournamentRequestBody.builder()
                        .playerId(ctx.registeredPlayer.getWalletData().getWalletUUID())
                        .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                        .amount(singleTournamentAmount)
                        .transactionId(transactionId)
                        .roundId(UUID.randomUUID().toString())
                        .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                        .providerUuid(ctx.gameLaunchData.getDbGameSession().getProviderUuid())
                        .build();
                ctx.allMadeTournamentRequests.add(tournamentRequestBody);

                var currentTournamentNumber = i + 1;
                step("Совершение турнирного начисления #" + currentTournamentNumber + " (ID: " + transactionId + ")", () -> {
                    var response = managerClient.tournament(
                            casinoId,
                            utils.createSignature(ApiEndpoints.TOURNAMENT, tournamentRequestBody),
                            tournamentRequestBody);

                    assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code");
                });
            }
        });

        step("NATS: Ожидание NATS-события tournament_won_from_gamble для последнего начисления", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.lastMadeTournamentTransactionId.equals(payload.getUuid());

            ctx.lastTournamentNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(ctx.lastTournamentNatsEvent, "nats.tournament_won_from_gamble_event");
        });

        step("Redis: Определение вытесненного турнирного начисления", () -> {
            WalletFullData aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.lastTournamentNatsEvent.getSequence());

            var gamblingTransactionsInRedis = aggregate.getGambling();
            var transactionIdsCurrentlyInRedis = gamblingTransactionsInRedis.keySet();

            var displacedTransactionIds = ctx.allMadeTournamentRequests.stream()
                    .map(TournamentRequestBody::getTransactionId).collect(Collectors.toCollection(HashSet::new));
            displacedTransactionIds.removeAll(transactionIdsCurrentlyInRedis);
            assertEquals(1, displacedTransactionIds.size(), "redis.displaced_transaction.count");
            var displacedTxId = displacedTransactionIds.iterator().next();

            ctx.displacedTournamentRequestToDuplicate = ctx.allMadeTournamentRequests.stream()
                    .filter(req -> req.getTransactionId().equals(displacedTxId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));
        });

        step("Manager API: Попытка дублирования вытесненного турнирного начисления (ID: " + ctx.displacedTournamentRequestToDuplicate.getTransactionId() + ") и проверка ошибки", () -> {
            var duplicateTournamentAttemptRequest = TournamentRequestBody.builder()
                    .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(ctx.displacedTournamentRequestToDuplicate.getAmount())
                    .transactionId(ctx.displacedTournamentRequestToDuplicate.getTransactionId())
                    .roundId(ctx.displacedTournamentRequestToDuplicate.getRoundId())
                    .gameUuid(ctx.displacedTournamentRequestToDuplicate.getGameUuid())
                    .providerUuid(ctx.displacedTournamentRequestToDuplicate.getProviderUuid())
                    .build();

            var duplicateResponse = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, duplicateTournamentAttemptRequest),
                    duplicateTournamentAttemptRequest
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка идемпотентного ответа на дубликат вытесненного турнирного начисления",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(responseBody, "manager_api.response_body"),
                    () -> assertEquals(duplicateTournamentAttemptRequest.getTransactionId(), responseBody.getTransactionId(), "manager_api.transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.getBalance()), "manager_api.balance")
            );
        });
    }
}