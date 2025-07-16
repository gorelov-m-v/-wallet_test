package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
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
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
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
import java.util.UUID;
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
@TmsLink("NW-44")
class CasinoLossLimitWhenRollbackFromGambleTest {
    @Autowired private FapiClient publicClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletRedisClient redisClient;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    @Test
    @DisplayName("Изменение остатка CasinoLossLimit при получении роллбэка в казино")
    void test() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            RollbackRequestBody rollbackRequestBody;
            NatsMessage<NatsGamblingEventPayload> rollbackEvent;
            BigDecimal limitAmount;
            BigDecimal betAmount;
            BigDecimal rollbackAmount;
            BigDecimal expectedRestAmountAfterRollback;
            BigDecimal expectedSpentAmountAfterRollback;
        }
        final TestData testData = new TestData();

        testData.limitAmount =  new BigDecimal("150.12");
        testData.betAmount = new BigDecimal("10.15");
        testData.rollbackAmount = testData.betAmount;
        testData.expectedSpentAmountAfterRollback = BigDecimal.ZERO;
        testData.expectedRestAmountAfterRollback = testData.limitAmount;

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

                assertNotNull(limitCreateEvent, "nats.limit_changed_v2_event");
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

        step("Manager API: Получение роллбэка", () -> {
            testData.rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .amount(testData.rollbackAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(testData.betRequestBody.getTransactionId())
                    .roundId(testData.betRequestBody.getRoundId())
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .roundClosed(true)
                    .build();

            var response = managerClient.rollback(
                    casinoId,
                    utils.createSignature(ApiEndpoints.ROLLBACK, testData.rollbackRequestBody),
                    testData.rollbackRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.rollback.status_code");

            step("Sub-step NATS: Проверка поступления события rollbacked_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.ROLLBACKED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                testData.rollbackRequestBody.getTransactionId().equals(payload.getUuid());

                testData.rollbackEvent = natsClient.findMessageAsync(
                        subject,
                        NatsGamblingEventPayload.class,
                        filter).get();

                assertNotNull(testData.rollbackEvent, "nats.rollbacked_from_gamble_event");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.rollbackEvent.getSequence());

            assertAll(
                    () -> assertEquals(0, testData.expectedRestAmountAfterRollback.compareTo(aggregate.getLimits().getFirst().getRest()), "redis.limit.rest"),
                    () -> assertEquals(0, testData.expectedSpentAmountAfterRollback.compareTo(aggregate.getLimits().getFirst().getSpent()), "redis.limit.spent"),
                    () -> assertEquals(0, testData.limitAmount.compareTo(aggregate.getLimits().getFirst().getAmount()), "redis.limit.amount")
            );
        });
    }
}