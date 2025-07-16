package com.uplatform.wallet_tests.tests.wallet.limit.turnover;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
@TmsLink("")
class TurnoverLimitWhenWinFromIframeTest {
    @Autowired private FapiClient publicClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletRedisClient redisClient;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);
    private static final BigDecimal betAmount = generateBigDecimalAmount(limitAmountBase);
    private static final BigDecimal winAmount = generateBigDecimalAmount(initialAdjustmentAmount);

    @Test
    @DisplayName("Проверка состояния TurnoverLimit после ставки и последующего выигрыша")
    void shouldUpdateTurnoverLimitCorrectlyAfterWinFromIframe() {
        final class TestData {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            NatsMessage<NatsBettingEventPayload> winEvent;
            BigDecimal expectedRestAmountAfterOperations;
            BigDecimal expectedSpentAmountAfterOperations;
        }
        final TestData testData = new TestData();

        testData.expectedSpentAmountAfterOperations = betAmount;
        testData.expectedRestAmountAfterOperations = limitAmountBase.subtract(testData.expectedSpentAmountAfterOperations);

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
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
                                payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                                NatsLimitType.TURNOVER_FUNDS.getValue().equals(payload.getLimits().get(0).getLimitType()) &&
                                NatsLimitIntervalType.DAILY.getValue().equals(payload.getLimits().get(0).getIntervalType()) &&
                                request.getCurrency().equals(payload.getLimits().get(0).getCurrencyCode());

                testData.limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();
                assertNotNull(testData.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Совершение ставки на спорт (BET)", () -> {
            testData.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(testData.registeredPlayer.getWalletData().getPlayerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .build();

            testData.betRequestBody = generateRequest(testData.betInputData);
            var response = managerClient.makePayment(testData.betRequestBody);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment(BET).status_code");
        });

        step("Manager API: Получение выигрыша по ставке (WIN)", () -> {
            testData.betRequestBody.setSumm(winAmount.toString());
            testData.betRequestBody.setType(NatsBettingTransactionOperation.WIN);

            var response = managerClient.makePayment(testData.betRequestBody);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment(WIN).status_code");

            step("Sub-step NATS: Проверка поступления события won_from_iframe", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.WON_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                                testData.betRequestBody.getBetId().equals(payload.getBetId());

                testData.winEvent = natsClient.findMessageAsync(subject, NatsBettingEventPayload.class, filter).get();

                assertAll("nats.won_from_iframe_event.content_validation",
                        () -> assertNotNull(testData.winEvent, "nats.won_from_iframe_event"),
                        () -> assertEquals(0, winAmount.compareTo(testData.winEvent.getPayload().getAmount()), "nats.won_from_iframe_event.payload.amount"),
                        () -> assertEquals(NatsBettingTransactionOperation.WIN, testData.winEvent.getPayload().getType(), "nats.won_from_iframe_event.payload.operation"),
                        () -> assertEquals(testData.betRequestBody.getBetId(), testData.winEvent.getPayload().getBetId(), "nats.won_from_iframe_event.payload.betId")
                );
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате после всех операций", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.winEvent.getSequence());

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) testData.winEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet.limits_list_not_empty"),
                    () -> {
                        var turnoverLimitOpt = aggregate.getLimits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType()) &&
                                        testData.registeredPlayer.getWalletData().getCurrency().equals(l.getCurrencyCode()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit_present");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, testData.expectedRestAmountAfterOperations.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, testData.expectedSpentAmountAfterOperations.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, limitAmountBase.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}