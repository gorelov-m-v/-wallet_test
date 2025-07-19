package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.kafka.dto.LimitMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий процесс создания лимита на проигрыш в казино
 * (CasinoLossLimit) с различными периодами действия (ежедневный, еженедельный,
 * ежемесячный) и его корректное отражение в различных системах.
 *
 * <p>Каждая итерация параметризованного теста выполняется с полностью изолированным
 * состоянием, включая создание нового игрока.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API:
 *     <ul>
 *       <li>Установка лимита на проигрыш через FAPI ({@code /profile/limit/casino-loss}).</li>
 *       <li>Получение списка лимитов игрока через FAPI ({@code /profile/limit/casino-loss}).</li>
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
 *   <li>{@code MONTHLY} - ежемесячный.</li>
 * </ul>
 */

@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Limits") @Tag("Wallet") @Tag("CasinoLossLimit")
public class CasinoLossLimitCreateParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal limitAmount = new BigDecimal("100.15");

    static Stream<Arguments> periodProvider() {
        return Stream.of(
                arguments(NatsLimitIntervalType.DAILY),
                arguments(NatsLimitIntervalType.WEEKLY),
                arguments(NatsLimitIntervalType.MONTHLY)
        );
    }

    @ParameterizedTest(name = "период = {0}")
    @MethodSource("periodProvider")
    @DisplayName("Создание, проверка и получение CasinoLossLimit:")
    void createAndVerifyCasinoLossLimit(NatsLimitIntervalType periodType) {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            SetCasinoLossLimitRequest setLimitRequest;
            LimitMessage kafkaLimitMessage;
            NatsMessage<NatsLimitChangedV2Payload> natsLimitChangeEvent;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API (FAPI): Установка лимита на проигрыш в казино", () -> {
            ctx.setLimitRequest = SetCasinoLossLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .type(periodType)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.setLimitRequest);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_casino_loss_limit.status_code");
        });

        step("Kafka: Проверка получения события limits.v2", () -> {
            var expectedAmount = limitAmount.stripTrailingZeros().toPlainString();
            ctx.kafkaLimitMessage = limitKafkaClient.expectLimitMessage(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    NatsLimitType.CASINO_LOSS.getValue(),
                    ctx.registeredPlayer.getWalletData().getCurrency(),
                    expectedAmount
            );

            assertNotNull(ctx.kafkaLimitMessage, "kafka.limits_v2_event.message_not_null");
            assertAll("kafka.limits_v2_event.content_validation",
                    () -> assertEquals(NatsLimitType.CASINO_LOSS.getValue(), ctx.kafkaLimitMessage.getLimitType(), "kafka.limits_v2_event.limitType"),
                    () -> assertEquals(periodType.getValue(), ctx.kafkaLimitMessage.getIntervalType(), "kafka.limits_v2_event.intervalType"),
                    () -> assertEquals(0, limitAmount.compareTo(new BigDecimal(ctx.kafkaLimitMessage.getAmount())), "kafka.limits_v2_event.amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), ctx.kafkaLimitMessage.getCurrencyCode(), "kafka.limits_v2_event.currencyCode"),
                    () -> assertNotNull(ctx.kafkaLimitMessage.getId(), "kafka.limits_v2_event.id"),
                    () -> assertNotNull(ctx.kafkaLimitMessage.getStartedAt(), "kafka.limits_v2_event.startedAt"),
                    () -> assertNotNull(ctx.kafkaLimitMessage.getExpiresAt(), "kafka.limits_v2_event.expiresAt")
            );
        });

        step("NATS: Проверка получения события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                            payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                            ctx.kafkaLimitMessage.getId().equals(payload.getLimits().get(0).getExternalId());

            ctx.natsLimitChangeEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();
            assertNotNull(ctx.natsLimitChangeEvent, "nats.limit_changed_v2_event.message_not_null");
            assertNotNull(ctx.natsLimitChangeEvent.getPayload(), "nats.limit_changed_v2_event.payload_not_null");
            assertNotNull(ctx.natsLimitChangeEvent.getPayload().getLimits(), "nats.limit_changed_v2_event.payload.limits_list_not_null");
            assertFalse(ctx.natsLimitChangeEvent.getPayload().getLimits().isEmpty(), "nats.limit_changed_v2_event.payload.limits_list_not_empty");

            var natsLimit = ctx.natsLimitChangeEvent.getPayload().getLimits().get(0);
            assertAll("nats.limit_changed_v2_event.content_validation",
                    () -> assertEquals(NatsLimitEventType.CREATED.getValue(), ctx.natsLimitChangeEvent.getPayload().getEventType(), "nats.limit_changed_v2_event.payload.eventType"),
                    () -> assertEquals(ctx.kafkaLimitMessage.getId(), natsLimit.getExternalId(), "nats.limit_changed_v2_event.limit.externalId"),
                    () -> assertEquals(NatsLimitType.CASINO_LOSS.getValue(), natsLimit.getLimitType(), "nats.limit_changed_v2_event.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), natsLimit.getIntervalType(), "nats.limit_changed_v2_event.limit.intervalType"),
                    () -> assertEquals(0, limitAmount.compareTo(natsLimit.getAmount()), "nats.limit_changed_v2_event.limit.amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), natsLimit.getCurrencyCode(), "nats.limit_changed_v2_event.limit.currencyCode"),
                    () -> assertNotNull(natsLimit.getStartedAt(), "nats.limit_changed_v2_event.limit.startedAt"),
                    () -> assertEquals(ctx.kafkaLimitMessage.getExpiresAt(), natsLimit.getExpiresAt(), "nats.limit_changed_v2_event.limit.expiresAt"),
                    () -> assertTrue(natsLimit.getStatus(), "nats.limit_changed_v2_event.limit.status_is_true")
            );
        });

        step("Kafka Projection: Сравнение данных из NATS и Kafka Wallet Projection", () -> {
            var projectionMsg = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.natsLimitChangeEvent.getSequence());

            assertNotNull(projectionMsg, "kafka.wallet_projection.message_not_null");
            assertTrue(utils.areEquivalent(projectionMsg, ctx.natsLimitChangeEvent), "kafka.wallet_projection.equivalent_to_nats");
        });

        step("Redis (Wallet Aggregate): Проверка данных лимита в агрегате кошелька", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.natsLimitChangeEvent.getSequence());

            assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet_aggregate.limits_list_not_empty");

            var redisLimitOpt = aggregate.getLimits().stream()
                    .filter(l ->
                            ctx.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getExternalID()) &&
                                    NatsLimitType.CASINO_LOSS.getValue().equals(l.getLimitType()))
                    .findFirst();

            assertTrue(redisLimitOpt.isPresent(), "redis.wallet_aggregate.limit_found");
            var redisLimit = redisLimitOpt.get();

            assertAll("redis.wallet_aggregate.limit_content_validation",
                    () -> assertEquals(ctx.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId(), redisLimit.getExternalID(), "redis.wallet_aggregate.limit.externalId"),
                    () -> assertEquals(NatsLimitType.CASINO_LOSS.getValue(), redisLimit.getLimitType(), "redis.wallet_aggregate.limit.limitType"),
                    () -> assertEquals(periodType.getValue(), redisLimit.getIntervalType(), "redis.wallet_aggregate.limit.intervalType"),
                    () -> assertEquals(0, limitAmount.compareTo(redisLimit.getAmount()), "redis.wallet_aggregate.limit.amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(redisLimit.getSpent()), "redis.wallet_aggregate.limit.spent_is_zero"),
                    () -> assertEquals(0, limitAmount.compareTo(redisLimit.getRest()), "redis.wallet_aggregate.limit.rest_equals_amount"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), redisLimit.getCurrencyCode(), "redis.wallet_aggregate.limit.currencyCode"),
                    () -> assertNotNull(redisLimit.getStartedAt(), "redis.wallet_aggregate.limit.startedAt"),
                    () -> assertNotNull(redisLimit.getExpiresAt(), "redis.wallet_aggregate.limit.expiresAt"),
                    () -> assertTrue(redisLimit.isStatus(), "redis.wallet_aggregate.limit.status_is_true")
            );
        });

        step("CAP: Получение и валидация лимитов игрока", () -> {
            var response = capAdminClient.getPlayerLimits(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.get_player_limits.status_code");
            assertNotNull(response.getBody(), "cap.get_player_limits.response_body_not_null");
            assertNotNull(response.getBody().getData(), "cap.get_player_limits.response_body.data_list_not_null");
            assertFalse(response.getBody().getData().isEmpty(), "cap.get_player_limits.response_body.data_list_not_empty");

            var capLimitOpt = response.getBody().getData().stream()
                    .filter(l -> limitAmount.compareTo(l.getAmount()) == 0)
                    .findFirst();

            assertTrue(capLimitOpt.isPresent(), "cap.get_player_limits");

            var capLimit = capLimitOpt.get();

            assertAll("cap.get_player_limits.limit_content_validation",
                    () -> assertTrue(capLimit.isStatus(), "cap.get_player_limits.limit.status_is_true"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), capLimit.getCurrency(), "cap.get_player_limits.limit.currency"),
                    () -> assertEquals(0, limitAmount.compareTo(capLimit.getAmount()), "cap.get_player_limits.limit.amount"),
                    () -> {
                        assertNotNull(capLimit.getRest(), "cap.get_player_limits.limit.rest_is_not_null");
                        assertEquals(0, limitAmount.compareTo(capLimit.getRest()), "cap.get_player_limits.limit.rest_equals_amount");
                    },
                    () -> {
                        if (capLimit.getSpent() != null) {
                            assertEquals(0, BigDecimal.ZERO.compareTo(capLimit.getSpent()), "cap.get_player_limits.limit.spent_is_zero_if_not_null");
                        }
                    },
                    () -> assertNotNull(capLimit.getCreatedAt(), "cap.get_player_limits.limit.createdAt"),
                    () -> assertNull(capLimit.getDeactivatedAt(), "cap.get_player_limits.limit.deactivatedAt_is_null_for_active"),
                    () -> assertNotNull(capLimit.getStartedAt(), "cap.get_player_limits.limit.startedAt"),
                    () -> assertNotNull(capLimit.getExpiresAt(), "cap.get_player_limits.limit.expiresAt")
            );
        });

        step("Public API: Получение и валидация списка лимитов игрока", () -> {
            var response = publicClient.getCasinoLossLimits(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken()
            );

            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_casino_loss_limits.status_code");
            assertNotNull(response.getBody(), "fapi.get_casino_loss_limits.response_body_not_null");
            assertFalse(response.getBody().isEmpty(), "fapi.get_casino_loss_limits.response_body_list_not_empty");

            var fapiLimitOpt = response.getBody().stream()
                    .filter(l -> {
                        boolean idMatch = ctx.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId().equals(l.getId());
                        boolean typeMatch = periodType.getValue().equalsIgnoreCase(l.getType());
                        return idMatch && typeMatch;
                    })
                    .findFirst();

            assertTrue(fapiLimitOpt.isPresent(), "fapi.get_casino_loss_limits.casino_loss_limit_not_found");
            var fapiLimit = fapiLimitOpt.get();

            assertAll("fapi.get_casino_loss_limits.limit_content_validation",
                    () -> assertEquals(ctx.natsLimitChangeEvent.getPayload().getLimits().get(0).getExternalId(), fapiLimit.getId(), "fapi.get_casino_loss_limits.limit.id"),
                    () -> assertEquals(periodType.getValue(), fapiLimit.getType(), "fapi.get_casino_loss_limits.limit.type_period"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), fapiLimit.getCurrency(), "fapi.get_casino_loss_limits.limit.currency"),
                    () -> assertTrue(fapiLimit.isStatus(), "fapi.get_casino_loss_limits.limit.status_is_true"),
                    () -> assertEquals(0, limitAmount.compareTo(fapiLimit.getAmount()), "fapi.get_casino_loss_limits.limit.amount"),
                    () -> assertEquals(0, limitAmount.compareTo(fapiLimit.getRest()), "fapi.get_casino_loss_limits.limit.rest_equals_amount"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(fapiLimit.getSpent()), "fapi.get_casino_loss_limits.limit.spent_is_zero"),
                    () -> assertNotNull(fapiLimit.getStartedAt(), "fapi.get_casino_loss_limits.limit.startedAt"),
                    () -> assertNotNull(fapiLimit.getExpiresAt(), "fapi.get_casino_loss_limits.limit.expiresAt"),
                    () -> assertNull(fapiLimit.getDeactivatedAt(), "fapi.get_casino_loss_limits.limit.deactivatedAt_is_null_for_active_limit"),
                    () -> assertFalse(fapiLimit.isRequired(), "fapi.get_casino_loss_limits.limit.isRequired_flag"),
                    () -> {
                        assertNotNull(fapiLimit.getUpcomingChanges(), "fapi.get_casino_loss_limits.limit.upcomingChanges_list_not_null");
                        assertTrue(fapiLimit.getUpcomingChanges().isEmpty(), "fapi.get_casino_loss_limits.limit.upcomingChanges_is_empty_for_new");
                    }
            );
        });
    }
}
