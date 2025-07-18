package com.uplatform.wallet_tests.tests.wallet.betting.refund;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Позитивные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
/**
 * Рефанд для вытесненной из Redis ставки.
 *
 * Тест формирует цепочку iFrame ставок до превышения лимита и возвращает вытесненную через refund.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> новый пользователь.</li>
 *   <li><b>Основное действие:</b> цикл ставок и refund вытесненной.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и success.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 *   <li>NATS: betted_from_iframe (для sequence)</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class RefundDisplacedIframeBetTest extends BaseTest {

    private static final BigDecimal singleBetAmount = new BigDecimal("1.00");
    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("550.00");
    private static final BigDecimal refundCoeff = new BigDecimal("1.00");

    @Test
    @DisplayName("API рефанд iFrame ставки вытесненной из Redis")
    void testApiRefundByModifyingOriginalRequestForDisplacedIframeBet() {
        final int maxIframeCountInRedis = configProvider.getEnvironmentConfig().getRedis().getAggregate().getMaxIframeCount();
        final int currentTransactionCountToMake = maxIframeCountInRedis + 1;

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            List<MakePaymentRequest> madeBetsRequests = new ArrayList<>();
            Long lastMadeBetId;
            MakePaymentRequest originalBetRequestToRefund;
            NatsMessage<NatsBettingEventPayload> lastBetNatsEvent;
            BigDecimal currentCalculatedBalance;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            testData.currentCalculatedBalance = testData.registeredPlayer.getWalletData().getBalance();
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение " + currentTransactionCountToMake + " iFrame ставок", () -> {
            for (int i = 0; i < currentTransactionCountToMake; i++) {
                var betInputData = MakePaymentData.builder()
                        .type(NatsBettingTransactionOperation.BET)
                        .playerId(testData.registeredPlayer.getWalletData().getPlayerUUID())
                        .summ(singleBetAmount.toPlainString())
                        .couponType(NatsBettingCouponType.SINGLE)
                        .currency(testData.registeredPlayer.getWalletData().getCurrency())
                        .build();
                var betRequestBody = MakePaymentRequestGenerator.generateRequest(betInputData);

                testData.madeBetsRequests.add(betRequestBody);

                if (i == currentTransactionCountToMake - 1) {
                    testData.lastMadeBetId = betRequestBody.getBetId();
                }

                var currentBetNumber = i + 1;
                var currentBetRequest = betRequestBody;

                step("Совершение iFrame ставки #" + currentBetNumber + " с BetID: " + currentBetRequest.getBetId(), () -> {
                    var response = managerClient.makePayment(currentBetRequest);
                    assertNotNull(response.getBody(), "manager_api.body_not_null");
                    assertAll("Проверка ответа API для iFrame ставки #" + currentBetNumber,
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                            () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode")
                    );
                    testData.currentCalculatedBalance = testData.currentCalculatedBalance.subtract(singleBetAmount);
                });
            }
            assertEquals(currentTransactionCountToMake, testData.madeBetsRequests.size(), "bet.list.size");
        });

        step("NATS: Ожидание NATS-события betted_from_iframe для последней ставки (для sequence)", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                            Objects.equals(testData.lastMadeBetId, payload.getBetId());

            testData.lastBetNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsBettingEventPayload.class,
                    filter).get();
            assertNotNull(testData.lastBetNatsEvent, "nats.betted_from_iframe");
        });

        step("Определение вытесненных iFrame ставок из Redis", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.lastBetNatsEvent.getSequence());
            var iFrameRecordsInRedis = aggregate.getIFrameRecords();
            var allMadeBetIds = testData.madeBetsRequests.stream()
                    .map(MakePaymentRequest::getBetId)
                    .collect(Collectors.toSet());
            var betIdsCurrentlyInRedis = iFrameRecordsInRedis.stream()
                    .map(WalletFullData.IFrameRecord::getBetID)
                    .collect(Collectors.toSet());

            var displacedBetIds = new HashSet<>(allMadeBetIds);
            displacedBetIds.removeAll(betIdsCurrentlyInRedis);
            var betIdToRefund = displacedBetIds.iterator().next();

            testData.originalBetRequestToRefund = testData.madeBetsRequests.stream()
                    .filter(betReq -> Objects.equals(betReq.getBetId(), betIdToRefund))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));
        });

        step("Manager API: Рефанд вытесненной iFrame ставки (модификацией исходного запроса) и проверка ответа", () -> {
            var requestForRefund = testData.originalBetRequestToRefund;
            requestForRefund.setType(NatsBettingTransactionOperation.REFUND);
            requestForRefund.setTotalCoef(refundCoeff.toString());
            requestForRefund.setTime(System.currentTimeMillis() / 1000L);

            var response = managerClient.makePayment(requestForRefund);
            assertNotNull(response.getBody(), "manager_api.body_not_null");
            assertAll("Проверка ответа API на рефанд вытесненной iFrame ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.refund.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.refund.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.refund.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.refund.body.description")
            );
        });
    }
}