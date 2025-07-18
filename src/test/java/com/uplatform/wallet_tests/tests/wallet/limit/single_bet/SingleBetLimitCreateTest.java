package com.uplatform.wallet_tests.tests.wallet.limit.single_bet;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.kafka.client.LimitKafkaClient;
import com.uplatform.wallet_tests.api.kafka.client.WalletProjectionKafkaClient;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.BLOCKER)
@Epic("Limits")
@Feature("SingleBetLimit")
@Suite("Позитивные сценарии: SingleBetLimit")
@Tag("Limits") @Tag("Wallet")
public class SingleBetLimitCreateTest extends BaseTest {
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private FapiClient publicClient;
    @Autowired private LimitKafkaClient limitKafkaClient;
    @Autowired private WalletProjectionKafkaClient walletProjectionKafkaClient;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletRedisClient redisClient;
    @Autowired private CapAdminClient capAdminClient;
    @Autowired private TestUtils utils;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    @Test
    @DisplayName("Создание single-bet лимита")
    void createSingleBetLimit() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            BigDecimal limitAmount;
            SetSingleBetLimitRequest singleBetLimitRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
        }
        final TestData testData = new TestData();

        testData.limitAmount = new BigDecimal("100.15");

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Public API: Установка лимита на одиночную ставку", () -> {
            testData.singleBetLimitRequest = SetSingleBetLimitRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .amount(testData.limitAmount.toString())
                    .build();

            var response = publicClient.setSingleBetLimit(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    testData.singleBetLimitRequest
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_single_bet_limit.status_code");
        });

        step("Kafka: Получение сообщения из топика limits.v2", () -> {
            var expectedAmount = testData.limitAmount.stripTrailingZeros().toPlainString();
            testData.kafkaLimitMessage = limitKafkaClient.expectLimitMessage(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    NatsLimitType.SINGLE_BET.getValue(),
                    testData.registeredPlayer.getWalletData().getCurrency(),
                    expectedAmount
            );
            assertNotNull(testData.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
        });

        step("NATS: Проверка поступления события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            testData.kafkaLimitMessage.getId().equals(payload.getLimits().get(0).getExternalId());

            testData.limitCreateEvent = natsClient.findMessageAsync(
                    subject,
                    NatsLimitChangedV2Payload.class,
                    filter).get();

            assertAll("nats.limit_changed_v2_event.content_validation",
                    () -> assertEquals(NatsLimitEventType.CREATED.getValue(), testData.limitCreateEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(testData.kafkaLimitMessage.getId(), testData.limitCreateEvent.getPayload().getLimits().get(0).getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(testData.kafkaLimitMessage.getLimitType(), testData.limitCreateEvent.getPayload().getLimits().get(0).getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertTrue(testData.limitCreateEvent.getPayload().getLimits().get(0).getIntervalType().isEmpty(), "nats.limit_changed_v2_event.limit.intervalType_empty"),
                    () -> assertEquals(testData.kafkaLimitMessage.getAmount(), testData.limitCreateEvent.getPayload().getLimits().get(0).getAmount().toString(), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(testData.kafkaLimitMessage.getCurrencyCode(), testData.limitCreateEvent.getPayload().getLimits().get(0).getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(testData.limitCreateEvent.getPayload().getLimits().get(0).getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertEquals(0, testData.limitCreateEvent.getPayload().getLimits().get(0).getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(testData.limitCreateEvent.getPayload().getLimits().get(0).getStatus(), "nats.limit_changed_v2_event.limit.status")
            );
        });

        step("Kafka: Сравнение сообщения из Kafka с событием из NATS", () -> {
            var kafkaMessage = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    testData.limitCreateEvent.getSequence());
            assertTrue(utils.areEquivalent(kafkaMessage, testData.limitCreateEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.limitCreateEvent.getSequence());

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getExternalId(), aggregate.getLimits().get(0).getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getLimitType(), aggregate.getLimits().get(0).getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertTrue(testData.limitCreateEvent.getPayload().getLimits().get(0).getIntervalType().isEmpty(), "redis.wallet_aggregate.limit.intervalType_empty"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getAmount(), aggregate.getLimits().get(0).getAmount(), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(BigDecimal.ZERO, aggregate.getLimits().get(0).getSpent(), "redis.wallet_aggregate.limit.spent_zero"),
                    () -> assertEquals(0, testData.limitCreateEvent.getPayload().getLimits().get(0).getAmount().compareTo(aggregate.getLimits().get(0).getRest()), "redis.wallet_aggregate.limit.rest"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getCurrencyCode(), aggregate.getLimits().get(0).getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(testData.limitCreateEvent.getPayload().getLimits().get(0).getStartedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertEquals(0, aggregate.getLimits().get(0).getExpiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(aggregate.getLimits().get(0).isStatus(), "redis.wallet_aggregate.limit.status")
            );
        });

        step("CAP: Получение лимитов игрока и их валидация", () -> {
            var response = capAdminClient.getPlayerLimits(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code"),
                    () -> assertTrue(response.getBody().getData().get(0).isStatus(), "cap.get_player_limits.limit.status"),
                    () -> assertTrue(response.getBody().getData().get(0).getPeriod().toString().isEmpty(), "cap.get_player_limits.limit.intervalType_empty"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getCurrencyCode(), response.getBody().getData().get(0).getCurrency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getAmount(), response.getBody().getData().get(0).getAmount(), "cap.get_player_limits.limit.amount"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getAmount(), response.getBody().getData().get(0).getRest(), "cap.get_player_limits.limit.rest"),
                    () -> assertNotNull(response.getBody().getData().get(0).getCreatedAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(response.getBody().getData().get(0).getDeactivatedAt(), "cap.get_player_limits.limit.deactivatedAt"),
                    () -> assertNotNull(response.getBody().getData().get(0).getStartedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNull(response.getBody().getData().get(0).getExpiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение лимитов игрока и их валидация", () -> {
            var response = publicClient.getSingleBetLimits(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertAll("fapi.get_single_bet_limits.limit_content_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_single_bet_limits.status_code"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getExternalId(), response.getBody().get(0).getId(), "fapi.get_single_bet_limits.limit.id"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getCurrencyCode(), response.getBody().get(0).getCurrency(), "fapi.get_single_bet_limits.limit.currency"),
                    () -> assertTrue(response.getBody().get(0).isStatus(), "fapi.get_single_bet_limits.limit.status"),
                    () -> assertEquals(testData.limitCreateEvent.getPayload().getLimits().get(0).getAmount(), response.getBody().get(0).getAmount(), "fapi.get_single_bet_limits.limit.amount"),
                    () -> assertTrue(response.getBody().get(0).getUpcomingChanges().isEmpty(), "fapi.get_single_bet_limits.limit.upcomingChanges_empty"),
                    () -> assertNull(response.getBody().get(0).getDeactivatedAt(), "fapi.get_single_bet_limits.limit.deactivatedAt"),
                    () -> assertTrue(response.getBody().get(0).isRequired(), "fapi.get_single_bet_limits.limit.required")
            );
        });
    }
}