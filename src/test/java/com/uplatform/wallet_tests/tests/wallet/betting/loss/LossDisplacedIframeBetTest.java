package com.uplatform.wallet_tests.tests.wallet.betting.loss;
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
 * Проверяет получение loss для ставки, вытесненной из Redis.
 *
 * Сначала выполняется серия ставок из iframe для превышения лимита записей,
 * после чего идет поиск вытесненной ставки и её проигрыш.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание пользователя с балансом.</li>
 *   <li><b>Основное действие:</b> превышение лимита iFrame и проигрыш вытесненной ставки.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и успешное тело.</li>
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
class LossDisplacedIframeBetTest extends BaseTest {

    private static final BigDecimal singleBetAmount = new BigDecimal("1.00");
    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("550.00");
    private static final BigDecimal lossAmountForDisplacedBet = BigDecimal.ZERO;

    @Test
    @DisplayName("API проигрыш iFrame ставки вытесненной из Redis")
    void testApiLossByModifyingOriginalRequestForDisplacedIframeBet() {
        final int maxIframeCountInRedis = configProvider.getEnvironmentConfig().getRedis().getAggregate().getMaxIframeCount();
        final int currentTransactionCountToMake = maxIframeCountInRedis + 1;

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            List<MakePaymentRequest> madeBetsRequests = new ArrayList<>();
            Long lastMadeBetId;
            MakePaymentRequest originalBetRequestToProcessLoss;
            NatsMessage<NatsBettingEventPayload> lastBetNatsEvent;
            BigDecimal currentCalculatedBalance;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            ctx.currentCalculatedBalance = ctx.registeredPlayer.getWalletData().getBalance();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение " + currentTransactionCountToMake + " iFrame ставок", () -> {
            for (int i = 0; i < currentTransactionCountToMake; i++) {
                var betInputData = MakePaymentData.builder()
                        .type(NatsBettingTransactionOperation.BET)
                        .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                        .summ(singleBetAmount.toPlainString())
                        .couponType(NatsBettingCouponType.SINGLE)
                        .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                        .totalCoef(new BigDecimal("2.0").toString())
                        .build();
                var betRequestBody = MakePaymentRequestGenerator.generateRequest(betInputData);

                ctx.madeBetsRequests.add(betRequestBody);

                if (i == currentTransactionCountToMake - 1) {
                    ctx.lastMadeBetId = betRequestBody.getBetId();
                }

                var currentBetNumber = i + 1;
                var currentBetRequest = betRequestBody;

                step("Совершение iFrame ставки #" + currentBetNumber + " с BetID: " + currentBetRequest.getBetId(), () -> {
                    var response = managerClient.makePayment(currentBetRequest);
                    assertNotNull(response.getBody(), "manager_api.make_payment.body_not_null");
                    assertAll("Проверка ответа API для iFrame ставки #" + currentBetNumber,
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                            () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode")
                    );
                    ctx.currentCalculatedBalance = ctx.currentCalculatedBalance.subtract(singleBetAmount);
                });
            }
            assertEquals(currentTransactionCountToMake, ctx.madeBetsRequests.size(), "bet.list.size");
        });

        step("NATS: Ожидание NATS-события betted_from_iframe для последней ставки (для sequence)", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                            Objects.equals(ctx.lastMadeBetId, payload.getBetId());

            ctx.lastBetNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsBettingEventPayload.class,
                    filter).get();
            assertNotNull(ctx.lastBetNatsEvent, "nats.betted_from_iframe");
        });

        step("Определение вытесненных iFrame ставок из Redis", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.lastBetNatsEvent.getSequence());

            var iFrameRecordsInRedis = aggregate.getIFrameRecords();
            var allMadeBetIds = ctx.madeBetsRequests.stream()
                    .map(MakePaymentRequest::getBetId)
                    .collect(Collectors.toSet());
            var betIdsCurrentlyInRedis = iFrameRecordsInRedis.stream()
                    .map(WalletFullData.IFrameRecord::getBetID)
                    .collect(Collectors.toSet());

            var displacedBetIds = new HashSet<>(allMadeBetIds);
            displacedBetIds.removeAll(betIdsCurrentlyInRedis);
            var betIdForLoss = displacedBetIds.iterator().next();

            ctx.originalBetRequestToProcessLoss = ctx.madeBetsRequests.stream()
                    .filter(betReq -> Objects.equals(betReq.getBetId(), betIdForLoss))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("test.displaced_request.not_found"));
        });

        step("Manager API: Проигрыш по вытесненной iFrame ставке (модификацией исходного запроса) и проверка ответа", () -> {
            var requestForLoss = ctx.originalBetRequestToProcessLoss;
            requestForLoss.setType(NatsBettingTransactionOperation.LOSS);
            requestForLoss.setSumm(lossAmountForDisplacedBet.toPlainString());
            requestForLoss.setTime(System.currentTimeMillis() / 1000L);

            var response = managerClient.makePayment(requestForLoss);

            assertAll("Проверка ответа API на проигрыш по вытесненной iFrame ставке",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.loss.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.loss.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.loss.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.loss.body.description")
            );
        });
    }
}