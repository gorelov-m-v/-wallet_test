package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseTest;

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
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий состояние лимита на оборот средств в агрегате игрока
 * после совершения ставки на спорт (betting), последующего сообщения о проигрыше,
 * и затем перерасчета результата на выигрыш через Manager API.
 * Тест проверяет, что значения лимита (rest, spent) соответствуют ожидаемым после этих операций.
 * Важно: Проигрыш и последующий перерасчет на выигрыш не должны изменять состояние лимита оборота,
 * так как лимит расходуется только в момент совершения самой ставки.
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API:
 *     <ul>
 *      <li>Совершение ставки через Manager API ({@code /make-payment} для betting, операция BET).</li>
 *      <li>Обработка проигрыша по ставке через Manager API ({@code /make-payment} для betting, операция LOSS).</li>
 *      <li>Обработка перерасчета на выигрыш по ставке через Manager API ({@code /make-payment} для betting, операция WIN).</li>
 *     </ul>
 *   </li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code recalculated_from_iframe} через NATS при обработке перерасчета.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Проверка состояния лимита игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>})
 *       после события перерасчета.</li>
 * </ul>
 *
 * <p><b>Проверяемый тип ставки ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code BET} - ставка на спорт.</li>
 *   <li>{@code LOSS} - проигрыш по ставке.</li>
 *   <li>{@code WIN} - выигрыш по ставке (в данном случае как результат перерасчета).</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenRecalculateLossToWinTest extends BaseTest {
    @Autowired private FapiClient publicClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletRedisClient redisClient;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("200.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);
    private static final BigDecimal betAmount = generateBigDecimalAmount(limitAmountBase);
    private static final BigDecimal lossAmount = BigDecimal.ZERO;
    private static final BigDecimal winAmountRecalculated = generateBigDecimalAmount(initialAdjustmentAmount);

    @Test
    @DisplayName("Проверка состояния TurnoverLimit после ставки, проигрыша и перерасчета на выигрыш")
    void shouldMaintainTurnoverLimitStateAfterBetLossAndRecalculationToWin() {
        final class TestData {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            NatsMessage<NatsBettingEventPayload> recalculatedEvent;
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

        step("Manager API: Получение проигрыша по ставке (LOSS)", () -> {
            testData.betRequestBody.setSumm(lossAmount.toString());
            testData.betRequestBody.setType(NatsBettingTransactionOperation.LOSS);

            var response = managerClient.makePayment(testData.betRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment(LOSS).status_code");
        });

        step("Manager API: Перерасчет результата на выигрыш (WIN)", () -> {
            testData.betRequestBody.setSumm(winAmountRecalculated.toString());
            testData.betRequestBody.setType(NatsBettingTransactionOperation.WIN);

            var response = managerClient.makePayment(testData.betRequestBody);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.make_payment(WIN_recalculated).status_code");

            step("Sub-step NATS: Проверка поступления события recalculated_from_iframe", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.RECALCULATED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                                Objects.equals(testData.betRequestBody.getBetId(), payload.getBetId());

                testData.recalculatedEvent = natsClient.findMessageAsync(subject, NatsBettingEventPayload.class, filter).get();

                assertAll("nats.recalculated_from_iframe_event.content_validation",
                        () -> assertNotNull(testData.recalculatedEvent, "nats.recalculated_from_iframe_event"),
                        () -> assertEquals(0, winAmountRecalculated.compareTo(testData.recalculatedEvent.getPayload().getAmount()), "nats.recalculated_from_iframe_event.payload.amount"),
                        () -> assertEquals(NatsBettingTransactionOperation.WIN, testData.recalculatedEvent.getPayload().getType(), "nats.recalculated_from_iframe_event.payload.operation"),
                        () -> assertEquals(testData.betRequestBody.getBetId(), testData.recalculatedEvent.getPayload().getBetId(), "nats.recalculated_from_iframe_event.payload.betId")
                );
            });
        });

        step("Redis(Wallet): Проверка состояния лимита в агрегате после всех операций", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.recalculatedEvent.getSequence());

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) testData.recalculatedEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
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