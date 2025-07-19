package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
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
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
class CasinoLossWhenRefundFromIframeTest extends BaseTest {

    @Test
    @DisplayName("Изменение остатка CasinoLossLimit при получении рефанда от iframe")
    void shouldRejectBetWhenGamblingDisabled() {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal limitAmount = new BigDecimal("150.12");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal refundCoefficient = new BigDecimal("1.00");

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsBettingEventPayload> refundEvent;
            BigDecimal expectedRest;
            BigDecimal expectedSpent;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedSpent = BigDecimal.ZERO;
        ctx.expectedRest = limitAmount.subtract(ctx.expectedSpent);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            var request = SetCasinoLossLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "public_api.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader);

                var limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();

                assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
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

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");
        });

        step("Manager API: Получение рефанда", () -> {
            ctx.betRequestBody.setType(NatsBettingTransactionOperation.REFUND);
            ctx.betRequestBody.setTotalCoef(refundCoefficient.toString());

            var response = managerClient.makePayment(ctx.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");

            step("NATS: Проверка поступления события refunded_from_iframe", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.REFUNDED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                                ctx.betRequestBody.getBetId() == payload.getBetId();

                ctx.refundEvent = natsClient.findMessageAsync(
                        subject,
                        NatsBettingEventPayload.class,
                        filter).get();

                assertNotNull(ctx.refundEvent, "nats.event.refunded_from_iframe");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.refundEvent.getSequence());

            var limit = aggregate.getLimits().get(0);
            assertAll(
                    () -> assertEquals(0, ctx.expectedRest.compareTo(limit.getRest()), "redis.aggregate.limit.rest"),
                    () -> assertEquals(0, ctx.expectedSpent.compareTo(limit.getSpent()), "redis.aggregate.limit.spent"),
                    () -> assertEquals(0, limitAmount.compareTo(limit.getAmount()), "redis.aggregate.limit.amount")
            );
        });
    }
}