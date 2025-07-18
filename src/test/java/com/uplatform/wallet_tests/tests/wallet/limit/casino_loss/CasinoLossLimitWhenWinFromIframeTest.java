package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class CasinoLossLimitWhenWinFromIframeTest extends BaseTest {
    @Autowired private FapiClient publicClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletRedisClient redisClient;

    @Test
    @DisplayName("Изменение остатка CasinoLossLimit при получении выигрыша от iframe")
    void shouldRejectBetWhenGamblingDisabled() {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal limitAmount = new BigDecimal("150.12");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal winAmount = new BigDecimal("20.77");

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsBettingEventPayload> winEvent;
            BigDecimal expectedRest;
            BigDecimal expectedSpent;
        }
        final TestData testData = new TestData();

        testData.expectedSpent = betAmount.subtract(winAmount);
        testData.expectedRest = limitAmount.subtract(testData.expectedSpent);

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            var request = SetCasinoLossLimitRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "public_api.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader);

                var limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();

                assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
            });
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            testData.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(testData.registeredPlayer.getWalletData().getPlayerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .build();

            testData.betRequestBody = generateRequest(testData.betInputData);

            var response = managerClient.makePayment(testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");
        });

        step("Manager API: Получение выигрыша", () -> {
            testData.betRequestBody.setSumm(winAmount.toString());
            testData.betRequestBody.setType(NatsBettingTransactionOperation.WIN);

            var response = managerClient.makePayment(testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");

            step("NATS: Проверка поступления события won_from_iframe", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.WON_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                                testData.betRequestBody.getBetId() == payload.getBetId();

                testData.winEvent = natsClient.findMessageAsync(
                        subject,
                        NatsBettingEventPayload.class,
                        filter).get();

                assertNotNull(testData.winEvent, "nats.event.won_from_iframe");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.winEvent.getSequence());

            var limit = aggregate.getLimits().get(0);
            assertAll(
                    () -> assertEquals(0, testData.expectedRest.compareTo(limit.getRest()), "redis.aggregate.limit.rest"),
                    () -> assertEquals(0, testData.expectedSpent.compareTo(limit.getSpent()), "redis.aggregate.limit.spent"),
                    () -> assertEquals(0, limitAmount.compareTo(limit.getAmount()), "redis.aggregate.limit.amount")
            );
        });
    }
}