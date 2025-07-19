package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий изменение лимита на оборот средств в агрегате игрока
 * при совершении ставки на спорт (betting) через Manager API.
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API: Совершение ставки через Manager API ({@code /make-payment} для betting).</li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code betted_from_iframe} через NATS при совершении ставки на спорт.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Обновление данных лимита и баланса игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 * </ul>
 *
 * <p><b>Проверяемый тип ставки ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code BET} - ставка на спорт.
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenBetFromIframeTest extends BaseTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);
    private static final BigDecimal betAmount = generateBigDecimalAmount(limitAmountBase);

    @Test
    @DisplayName("Изменение остатка TurnoverLimit при совершении ставки на спорт")
    void testTurnoverLimitChangeOnBetFromIframe() {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsBettingEventPayload> betEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal expectedRestAmountAfterBet;
            BigDecimal expectedSpentAmountAfterBet;
            BigDecimal expectedPlayerBalanceAfterBet;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedSpentAmountAfterBet = betAmount;
        ctx.expectedRestAmountAfterBet = limitAmountBase.subtract(ctx.expectedSpentAmountAfterBet);
        ctx.expectedPlayerBalanceAfterBet = initialAdjustmentAmount.subtract(betAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
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
                                payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                                NatsLimitType.TURNOVER_FUNDS.getValue().equals(payload.getLimits().get(0).getLimitType()) &&
                                NatsLimitIntervalType.DAILY.getValue().equals(payload.getLimits().get(0).getIntervalType()) &&
                                request.getCurrency().equals(payload.getLimits().get(0).getCurrencyCode());

                ctx.limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();

                assertNotNull(ctx.limitCreateEvent, "nats.limit_changed_v2_event.received");
            });
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            ctx.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .build();

            ctx.betRequestBody = generateRequest(ctx.betInputData);

            var response = managerClient.makePayment(ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment.status_code");

            step("Sub-step NATS: Проверка поступления события betted_from_iframe", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(typeHeader);

                ctx.betEvent = natsClient.findMessageAsync(
                        subject,
                        NatsBettingEventPayload.class,
                        filter).get();

                assertAll("nats.betted_from_iframe_event.content_validation",
                        () -> assertEquals(0, betAmount.compareTo(ctx.betEvent.getPayload().getAmount().negate()), "nats.betted_from_iframe_event.payload.amount"),
                        () -> assertEquals(NatsBettingTransactionOperation.BET, ctx.betEvent.getPayload().getType(), "nats.betted_from_iframe_event.payload.operation"),
                        () -> assertEquals(ctx.betRequestBody.getBetId(), ctx.betEvent.getPayload().getBetId(), "nats.betted_from_iframe_event.payload.betId")
                );
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.betEvent.getSequence());

            assertAll("redis.wallet.limit_balance_validation",
                    () -> assertEquals((int) ctx.betEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet.limits_list_not_empty"),
                    () -> {
                        var turnoverLimitOpt = aggregate.getLimits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType()) &&
                                        ctx.registeredPlayer.getWalletData().getCurrency().equals(l.getCurrencyCode()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit_present");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, ctx.expectedRestAmountAfterBet.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, ctx.expectedSpentAmountAfterBet.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, limitAmountBase.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}