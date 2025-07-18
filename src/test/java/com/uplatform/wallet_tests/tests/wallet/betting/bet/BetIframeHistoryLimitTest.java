package com.uplatform.wallet_tests.tests.wallet.betting.bet;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Позитивные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
/**
 * Проверяет ограничение числа записей ставок из iframe в Redis.
 *
 * Тест совершает серию ставок новым игроком, чтобы превысить лимит хранимых
 * записей iFrame, и убеждается, что старые записи корректно удаляются, а
 * баланс кошелька изменяется ожидаемым образом.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание пользователя с балансом.</li>
 *   <li><b>Основное действие:</b> последовательное совершение ставок через
 *   makePayment.</li>
 *   <li><b>Проверка ответа API:</b> для каждой ставки статус 200 и успешное
 *   тело.</li>
 *   <li><b>Проверка NATS:</b> получение события betted_from_iframe для
 *   последней ставки.</li>
 *   <li><b>Проверка Redis:</b> количество записей iFrame не превышает лимит и
 *   баланс уменьшен корректно.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 *   <li>NATS: событие betted_from_iframe</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class BetIframeHistoryLimitTest extends BaseTest {
    @Autowired private WalletRedisClient redisClient;
    @Autowired private NatsClient natsClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal singleBetAmount = new BigDecimal("1.00");
    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("550.00");

    @Test
    @DisplayName("Проверка лимита количества iFrame ставок (IFrameRecords) в Redis")
    void testIframeBetHistoryCountLimitInRedis() {
        final int maxIframeCountInRedis = configProvider.getEnvironmentConfig().getRedis().getAggregate().getMaxIframeCount();
        final int currentTransactionCountToMake = maxIframeCountInRedis + 1;

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            Long lastBetId;
            NatsMessage<NatsBettingEventPayload> lastBetEvent;
            BigDecimal currentBalance;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            testData.currentBalance = testData.registeredPlayer.getWalletData().getBalance();

            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение iFrame ставок", () -> {
            for (int i = 0; i < currentTransactionCountToMake; i++) {
                var betInputData = MakePaymentData.builder()
                        .type(NatsBettingTransactionOperation.BET)
                        .playerId(testData.registeredPlayer.getWalletData().getPlayerUUID())
                        .summ(singleBetAmount.toPlainString())
                        .couponType(NatsBettingCouponType.SINGLE)
                        .currency(testData.registeredPlayer.getWalletData().getCurrency())
                        .build();

                var betRequestBody = MakePaymentRequestGenerator.generateRequest(betInputData);

                if (i == currentTransactionCountToMake - 1) {
                    testData.lastBetId = betRequestBody.getBetId();
                }

                var currentBetNumber = i + 1;
                var currentBetIdForStep = betRequestBody.getBetId();

                step("Совершение iFrame ставки #" + currentBetNumber + " с BetID: " + currentBetIdForStep, () -> {
                    var response = managerClient.makePayment(betRequestBody);

                    assertAll("Проверка ответа API для iFrame ставки #" + currentBetNumber,
                            () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                            () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                            () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                            () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
                    );

                    testData.currentBalance = testData.currentBalance.subtract(singleBetAmount);
                });
            }
        });

        step("NATS: Ожидание NATS-события betted_from_iframe для последней ставки", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                            Objects.equals(testData.lastBetId, payload.getBetId());

            testData.lastBetEvent = natsClient.findMessageAsync(
                    subject,
                    NatsBettingEventPayload.class,
                    filter).get();

            assertNotNull(testData.lastBetEvent, "nats.betted_from_iframe");
        });

        step("Redis(Wallet): Получение и проверка данных кошелька (лимит iFrame ставок)", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.lastBetEvent.getSequence());

            var iFrameRecordsInRedis = aggregate.getIFrameRecords();

            assertAll("Проверка данных iFrame в Redis",
                    () -> assertEquals(maxIframeCountInRedis - 1, iFrameRecordsInRedis.size(),"redis.wallet.gambling.count"),
                    () -> assertEquals(0, testData.currentBalance.compareTo(aggregate.getBalance()),"redis.wallet.balance")
            );
        });
    }
}