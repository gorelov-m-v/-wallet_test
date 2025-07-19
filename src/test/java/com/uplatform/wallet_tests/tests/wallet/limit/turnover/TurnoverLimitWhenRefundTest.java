package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
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
 * при совершении ставки и последующем рефанде этой ставки в гемблинге.
 *
 * <p>Тест проверяет, что после рефанда сумма рефанда корректно возвращается в "rest" (остаток) лимита,
 * а "spent" (потрачено) уменьшается на сумму рефанда. Баланс игрока также должен быть восстановлен.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API:
 *     <ul>
 *       <li>Совершение ставки через Manager API ({@code /bet}).</li>
 *       <li>Совершение рефанда через Manager API ({@code /refund}).</li>
 *     </ul>
 *   </li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code betted_from_gamble} через NATS при совершении ставки.</li>
 *       <li>Передача события {@code refunded_from_gamble} через NATS при совершении рефанда.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Обновление данных лимита и баланса игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}) после рефанда.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenRefundTest extends BaseTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);
    private static final BigDecimal betAmount = generateBigDecimalAmount(limitAmountBase);
    private static final BigDecimal refundAmount = betAmount;

    @Test
    @DisplayName("Изменение остатка TurnoverLimit при получении полного рефанда на ставку в казино")
    void testTurnoverLimitChangeOnFullRefund() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RefundRequestBody refundRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            NatsMessage<NatsGamblingEventPayload> refundEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal expectedPlayerBalanceAfterBet;
            BigDecimal expectedPlayerBalanceAfterRefund;
            BigDecimal expectedRestAmountAfterRefund;
            BigDecimal expectedSpentAmountAfterRefund;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedPlayerBalanceAfterBet = initialAdjustmentAmount.subtract(betAmount);
        ctx.expectedPlayerBalanceAfterRefund = ctx.expectedPlayerBalanceAfterBet.add(refundAmount);

        ctx.expectedSpentAmountAfterRefund = betAmount.subtract(refundAmount);
        ctx.expectedRestAmountAfterRefund = limitAmountBase.subtract(betAmount).add(refundAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(limitAmountBase.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                                payload.getLimits().stream().anyMatch(l ->
                                        NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                                NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType())
                                );

                ctx.limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();
                assertNotNull(ctx.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertAll("manager_api.bet.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.bet.body_not_null"),
                    () -> assertEquals(ctx.betRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.bet.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedPlayerBalanceAfterBet.compareTo(response.getBody().getBalance()), "manager_api.bet.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.betRequestBody.getTransactionId().equals(payload.getUuid());

                ctx.betEvent = natsClient.findMessageAsync(subject, NatsGamblingEventPayload.class, filter).get();
                assertNotNull(ctx.betEvent, "nats.betted_from_gamble_event");
            });
        });

        step("Manager API: Получение рефанда", () -> {
            ctx.refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(refundAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(ctx.betRequestBody.getTransactionId())
                    .roundId(ctx.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, ctx.refundRequestBody),
                    ctx.refundRequestBody);

            assertAll("manager_api.refund.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.refund.body_not_null"),
                    () -> assertEquals(ctx.refundRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.refund.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedPlayerBalanceAfterRefund.compareTo(response.getBody().getBalance()), "manager_api.refund.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события refunded_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.REFUNDED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.refundRequestBody.getTransactionId().equals(payload.getUuid());

                ctx.refundEvent = natsClient.findMessageAsync(subject, NatsGamblingEventPayload.class, filter).get();
                assertNotNull(ctx.refundEvent, "nats.refunded_from_gamble_event");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате ПОСЛЕ РЕФАНДА", () -> {
            var expectedSequence = (int) ctx.refundEvent.getSequence();
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    expectedSequence);

            assertAll("redis.wallet.limit_balance_after_refund",
                    () -> assertEquals(expectedSequence, aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertEquals(0, ctx.expectedPlayerBalanceAfterRefund.compareTo(aggregate.getBalance()), "redis.wallet.balance"),
                    () -> assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet.limits_not_empty"),
                    () -> {
                        var turnoverLimitOpt = aggregate.getLimits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit_present");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, ctx.expectedRestAmountAfterRefund.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, ctx.expectedSpentAmountAfterRefund.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, limitAmountBase.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}