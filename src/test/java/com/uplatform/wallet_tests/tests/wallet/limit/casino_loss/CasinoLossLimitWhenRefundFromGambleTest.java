package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class CasinoLossLimitWhenRefundFromGambleTest extends BaseTest {
    @Autowired private FapiClient publicClient;

    @Test
    @DisplayName("Изменение остатка CasinoLossLimit при получении рефанда в казино")
    void test() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RefundRequestBody refundRequestBody;
            NatsMessage<NatsGamblingEventPayload> refundEvent;
            BigDecimal limitAmount;
            BigDecimal betAmount;
            BigDecimal refundAmount;
            BigDecimal expectedRestAmountAfterRefund;
            BigDecimal expectedSpentAmountAfterRefund;
        }
        final TestData testData = new TestData();

        testData.limitAmount =  new BigDecimal("150.12");
        testData.betAmount = new BigDecimal("10.15");
        testData.refundAmount = testData.betAmount;
        testData.expectedSpentAmountAfterRefund = BigDecimal.ZERO;
        testData.expectedRestAmountAfterRefund = testData.limitAmount;

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(new BigDecimal("2000.00"));
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            var request = SetCasinoLossLimitRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(testData.limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_casino_loss_limit.status_code");

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

        step("Manager API: Совершение ставки", () -> {
            testData.betRequestBody = BetRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, testData.betRequestBody),
                    testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
        });

        step("Manager API: Получение рефанда", () -> {
            testData.refundRequestBody = RefundRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.refundAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .betTransactionId(testData.betRequestBody.getTransactionId())
                    .roundId(testData.betRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var response = managerClient.refund(
                    casinoId,
                    utils.createSignature(ApiEndpoints.REFUND, testData.refundRequestBody),
                    testData.refundRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code");

            step("Sub-step NATS: Проверка поступления события refunded_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.REFUNDED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                testData.refundRequestBody.getTransactionId().equals(payload.getUuid());

                testData.refundEvent = natsClient.findMessageAsync(
                        subject,
                        NatsGamblingEventPayload.class,
                        filter).get();

                assertNotNull(testData.refundEvent, "nats.event.refunded_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.refundEvent.getSequence());

            assertAll(
                    () -> assertEquals(0, testData.expectedRestAmountAfterRefund.compareTo(aggregate.getLimits().get(0).getRest()), "redis.wallet.limit.rest"),
                    () -> assertEquals(0, testData.expectedSpentAmountAfterRefund.compareTo(aggregate.getLimits().get(0).getSpent()), "redis.wallet.limit.spent"),
                    () -> assertEquals(0, testData.limitAmount.compareTo(aggregate.getLimits().get(0).getAmount()), "redis.wallet.limit.amount")
            );
        });
    }
}