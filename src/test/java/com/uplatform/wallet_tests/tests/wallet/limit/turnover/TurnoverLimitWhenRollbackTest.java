package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
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

/**
 * Интеграционный тест, проверяющий изменение лимита на оборот средств в агрегате игрока
 * при совершении ставки и последующем роллбэке этой ставки в гемблинге.
 *
 * <p>Тест проверяет, что после роллбэка сумма роллбэка корректно возвращается в "rest" (остаток) лимита,
 * а "spent" (потрачено) уменьшается на сумму роллбэка (в идеале до 0, если роллбэк полный).
 * Баланс игрока также должен быть восстановлен до состояния перед ставкой.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API:
 *     <ul>
 *       <li>Совершение ставки через Manager API ({@code /bet}).</li>
 *       <li>Совершение роллбэка через Manager API ({@code /rollback}).</li>
 *     </ul>
 *   </li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code betted_from_gamble} через NATS при совершении ставки.</li>
 *       <li>Передача события {@code rollbacked_from_gamble} через NATS при совершении роллбэка.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Обновление данных лимита и баланса игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}) после роллбэка.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenRollbackTest extends BaseTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);
    private static final BigDecimal betAmount = generateBigDecimalAmount(limitAmountBase);
    private static final BigDecimal rollbackAmount = betAmount;

    @Test
    @DisplayName("Изменение остатка TurnoverLimit при получении полного роллбэка на ставку в казино")
    void testTurnoverLimitChangeOnFullRollback() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody rollbackRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            NatsMessage<NatsGamblingEventPayload> rollbackEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal expectedPlayerBalanceAfterBet;
            BigDecimal expectedPlayerBalanceAfterRollback;
            BigDecimal expectedRestAmountAfterRollback;
            BigDecimal expectedSpentAmountAfterRollback;
        }
        final TestData testData = new TestData();

        testData.expectedPlayerBalanceAfterBet = initialAdjustmentAmount.subtract(betAmount);
        testData.expectedPlayerBalanceAfterRollback = initialAdjustmentAmount;

        testData.expectedSpentAmountAfterRollback = BigDecimal.ZERO;
        testData.expectedRestAmountAfterRollback = limitAmountBase;

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(limitAmountBase.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                                payload.getLimits().stream().anyMatch(l ->
                                        NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                                NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType())
                                );

                testData.limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();
                assertNotNull(testData.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Совершение ставки", () -> {
            testData.betRequestBody = BetRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, testData.betRequestBody),
                    testData.betRequestBody);

            assertAll("manager_api.bet.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.bet.body_not_null"),
                    () -> assertEquals(testData.betRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.bet.body.transactionId"),
                    () -> assertEquals(0, testData.expectedPlayerBalanceAfterBet.compareTo(response.getBody().getBalance()), "manager_api.bet.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                testData.betRequestBody.getTransactionId().equals(payload.getUuid());

                testData.betEvent = natsClient.findMessageAsync(subject, NatsGamblingEventPayload.class, filter).get();
                assertNotNull(testData.betEvent, "nats.betted_from_gamble_event");
            });
        });

        step("Manager API: Получение роллбэка", () -> {
            testData.rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .playerId(testData.registeredPlayer.getWalletData().getPlayerUUID())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .amount(rollbackAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(testData.betRequestBody.getTransactionId())
                    .roundId(testData.betRequestBody.getRoundId())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .roundClosed(true)
                    .build();

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, testData.rollbackRequestBody),
                    testData.rollbackRequestBody);

            assertAll("manager_api.rollback.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.rollback.body_not_null"),
                    () -> assertEquals(testData.rollbackRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.rollback.body.transactionId"),
                    () -> assertEquals(0, testData.expectedPlayerBalanceAfterRollback.compareTo(response.getBody().getBalance()), "manager_api.rollback.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события rollbacked_from_gamble", () -> {
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
                assertNotNull(testData.rollbackEvent, "nats.rollbacked_from_gamble_event");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате ПОСЛЕ РОЛЛБЭКА", () -> {
            var expectedSequence = (int) testData.rollbackEvent.getSequence();
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    expectedSequence);

            assertAll("redis.wallet.limit_balance_after_rollback",
                    () -> assertEquals(expectedSequence, aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, testData.expectedPlayerBalanceAfterRollback.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet.limits_not_empty"),
                    () -> {
                        var turnoverLimitOpt = aggregate.getLimits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit_present");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, testData.expectedRestAmountAfterRollback.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, testData.expectedSpentAmountAfterRollback.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, limitAmountBase.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}