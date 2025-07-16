package com.uplatform.wallet_tests.tests.wallet.limit.turnover;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.kafka.client.LimitKafkaClient;
import com.uplatform.wallet_tests.api.kafka.client.WalletProjectionKafkaClient;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий процесс создания лимита на оборот средств (TurnoverLimit)
 * с различными периодами действия (ежедневный, еженедельный, ежемесячный)
 * и его корректное отражение в различных системах.
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным состоянием,
 * включая создание нового игрока.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API:
 *     <ul>
 *       <li>Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *       <li>Получение списка лимитов игрока через FAPI ({@code /profile/limit/turnover}).</li>
 *     </ul>
 *   </li>
 *   <li>Control Admin Panel (CAP) API: Получение лимитов игрока.</li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limits.v2} через Kafka при установке лимита.</li>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Проверка консистентности данных между Kafka (проекция кошелька) и NATS.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Проверка данных созданного лимита в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 * </ul>
 *
 * <p><b>Проверяемые типы периодов лимита ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType}):</b></p>
 * <ul>
 *   <li>{@code DAILY} - ежедневный.</li>
 *   <li>{@code WEEKLY} - еженедельный.</li>
 *   <li>{@code MONTHLY} - ежемесячный.
 * </ul>
 */
@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Limits") @Tag("Wallet") @Tag("TurnoverLimit")
@TmsLink("NW-36")
public class TurnoverLimitCreateParametrizedTest {
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private FapiClient publicClient;
    @Autowired private LimitKafkaClient limitKafkaClient;
    @Autowired private WalletProjectionKafkaClient walletProjectionKafkaClient;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletRedisClient redisClient;
    @Autowired private CapAdminClient capAdminClient;
    @Autowired private TestUtils utils;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY, true, "DAILY"),
                arguments(NatsLimitIntervalType.WEEKLY, false, "WEEKLY"),
                arguments(NatsLimitIntervalType.MONTHLY, false, "MONTHLY")
        );
    }

    /**
     * @param periodType Тип периода для устанавливаемого лимита.
     * @param isLimitRequired Флаг, указывающий, является ли лимит обязательным (проверяется в FAPI).
     */
    @ParameterizedTest(name = "период = {0}, обязательный = {1}")
    @MethodSource("periodProvider")
    @DisplayName("Создание, проверка и получение TurnoverLimit:")
    void testCreateAndVerifyTurnoverLimit(NatsLimitIntervalType periodType, boolean isLimitRequired, String periodName) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            SetTurnoverLimitRequest setLimitRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> natsLimitChangeEvent;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Public API (FAPI): Установка лимита на оборот средств", () -> {
            testData.setLimitRequest = SetTurnoverLimitRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .type(periodType)
                    .amount(limitAmountBase.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    testData.setLimitRequest);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");
        });

        step("Kafka: Проверка получения события limits.v2", () -> {
            testData.kafkaLimitMessage = limitKafkaClient.expectLimitMessage(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    NatsLimitType.TURNOVER_FUNDS.getValue(),
                    testData.registeredPlayer.getWalletData().getCurrency(),
                    limitAmountBase.toString()
            );

            assertNotNull(testData.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
            assertAll("kafka.limits_v2_event.content_validation",
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), testData.kafkaLimitMessage.getLimitType(), "kafka.limits_v2_event.limitType"),
                    () -> assertEquals(periodType.getValue(), testData.kafkaLimitMessage.getIntervalType(), "kafka.limits_v2_event.intervalType"),
                    () -> assertEquals(0, limitAmountBase.compareTo(new BigDecimal(testData.kafkaLimitMessage.getAmount())), "kafka.limits_v2_event.amount"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), testData.kafkaLimitMessage.getCurrencyCode(), "kafka.limits_v2_event.currencyCode"),
                    () -> assertNotNull(testData.kafkaLimitMessage.getId(), "kafka.limits_v2_event.id"),
                    () -> assertNotNull(testData.kafkaLimitMessage.getStartedAt(), "kafka.limits_v2_event.startedAt"),
                    () -> assertNotNull(testData.kafkaLimitMessage.getExpiresAt(), "kafka.limits_v2_event.expiresAt")
            );
        });

        step("NATS: Проверка получения события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            testData.kafkaLimitMessage.getId().equals(payload.getLimits().get(0).getExternalId());

            testData.natsLimitChangeEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();
            assertNotNull(testData.natsLimitChangeEvent, "nats.limit_changed_v2_event.message_not_null");
            assertNotNull(testData.natsLimitChangeEvent.getPayload(), "nats.limit_changed_v2_event.payload_not_null");
            assertNotNull(testData.natsLimitChangeEvent.getPayload().getLimits(), "nats.limit_changed_v2_event.payload.limits_list_not_null");
            assertFalse(testData.natsLimitChangeEvent.getPayload().getLimits().isEmpty(), "nats.limit_changed_v2_event.payload.limits_list_not_empty");

            var natsLimit = testData.natsLimitChangeEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.content_validation",
                    () -> assertEquals(NatsLimitEventType.CREATED.getValue(), testData.natsLimitChangeEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(testData.kafkaLimitMessage.getId(), natsLimit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), natsLimit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), natsLimit.getIntervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, limitAmountBase.compareTo(natsLimit.getAmount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), natsLimit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(natsLimit.getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertEquals(testData.kafkaLimitMessage.getExpiresAt(), natsLimit.getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(natsLimit.getStatus(), "nats.limit_changed_v2_event.limit.status_is_true")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    testData.natsLimitChangeEvent.getSequence());
            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, testData.natsLimitChangeEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis (Wallet Aggregate): Проверка данных лимита в агрегате кошелька", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.natsLimitChangeEvent.getSequence());

            assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.getLimits().stream()
                    .filter(l ->
                            testData.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getExternalID()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.turnover_limit_found_period_" + periodType.getValue());
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(testData.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.TURNOVER_FUNDS.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, limitAmountBase.compareTo(redisLimit.getAmount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.getSpent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, limitAmountBase.compareTo(redisLimit.getRest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.getStartedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertNotNull(redisLimit.getExpiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.isStatus(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение и валидация лимитов игрока", () -> {
            var response = capAdminClient.getPlayerLimits(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code");
            assertNotNull(response.getBody(), "cap.get_player_limits.response_body_not_null");
            assertNotNull(response.getBody().getData(), "cap.get_player_limits.response_body.data_list_not_null");

            var capLimitOpt = response.getBody().getData().stream()
                    .filter(l -> limitAmountBase.compareTo(l.getAmount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits.limit_not_found");
            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.isStatus(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), capLimit.getCurrency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, limitAmountBase.compareTo(capLimit.getAmount()), "cap.get_player_limits.limit.amount"),
                    () -> {
                        assertNotNull(capLimit.getRest(), "cap.get_player_limits.limit.rest_is_not_null");
                        assertEquals(0, limitAmountBase.compareTo(capLimit.getRest()), "cap.get_player_limits.limit.rest_equals_amount");
                    },
                    () -> assertNull(capLimit.getSpent(), "cap.get_player_limits.limit.spent_is_not_null"),
                    () -> assertNotNull(capLimit.getCreatedAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.getDeactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null_for_active"),
                    () -> assertNotNull(capLimit.getStartedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.getExpiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение и валидация списка лимитов игрока", () -> {
            var response = publicClient.getTurnoverLimits(
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_turnover_limits.status_code");
            assertNotNull(response.getBody(), "fapi.get_turnover_limits.response_body_not_null");
            assertFalse(response.getBody().isEmpty(), "fapi.get_turnover_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> {
                        boolean idMatch = testData.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getId());
                        boolean typeMatch = periodType.getValue().equalsIgnoreCase(l.getType());
                        return idMatch && typeMatch;
                    })
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_turnover_limits.turnover_limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_turnover_limits.limit_content_validation",
                    () -> assertEquals(testData.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId(), fapiLimit.getId(), "fapi.get_turnover_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.getType(), "fapi.get_turnover_limits.limit.type_period"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), fapiLimit.getCurrency(), "fapi.get_turnover_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.isStatus(), "fapi.get_turnover_limits.limit.status_is_true"),
                    () -> assertEquals(0, limitAmountBase.compareTo(fapiLimit.getAmount()), "fapi.get_turnover_limits.limit.amount"),
                    () -> assertEquals(0, limitAmountBase.compareTo(fapiLimit.getRest()), "fapi.get_turnover_limits.limit.rest_equals_amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(fapiLimit.getSpent()), "fapi.get_turnover_limits.limit.spent_is_zero"),
                    () -> assertNotNull(fapiLimit.getStartedAt(), "fapi.get_turnover_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.getExpiresAt(), "fapi.get_turnover_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.getDeactivatedAt(), "fapi.get_turnover_limits.limit.deactivatedAt_is_null_for_active_limit"),
                    () -> assertEquals(isLimitRequired, fapiLimit.isRequired(), "fapi.get_turnover_limits.limit.isRequired_flag"),
                    () -> {
                        assertNotNull(fapiLimit.getUpcomingChanges(), "fapi.get_turnover_limits.limit.upcomingChanges_list_not_null");
                        assertTrue(fapiLimit.getUpcomingChanges().isEmpty(), "fapi.get_turnover_limits.limit.upcomingChanges_is_empty_for_new");
                    }
            );
        });
    }
}