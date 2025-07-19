package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.*;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий изменение лимита на оборот средств в агрегате игрока
 * при совершении ставок различных типов (BET, TIPS, FREESPIN) в гемблинге.
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API: Совершение ставки через Manager API ({@code /bet}).</li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code betted_from_gamble} через NATS при совершении ставки.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Обновление данных лимита и баланса игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 * </ul>
 *
 * <p><b>Проверяемые типы ставок ({@link com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка.</li>
 *   <li>{@code TIPS} - чаевые.</li>
 *   <li>{@code FREESPIN} - бесплатные вращения.
 * </ul>
 *
 * <p><b>Проверяемые суммы ставок:</b></p>
 * <ul>
 *   <li>Динамически генерируемые ненулевые значения (меньше суммы лимита).</li>
 *   <li>Нулевые значения ({@code 0.00}).</li>
 *   <li>Значения, равные сумме установленного лимита.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenBetParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);

    static Stream<Arguments> betProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(limitAmountBase),
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        generateBigDecimalAmount(limitAmountBase),
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        generateBigDecimalAmount(limitAmountBase),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        limitAmountBase,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        limitAmountBase,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        limitAmountBase,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.BET,
                        NatsGamblingTransactionType.TYPE_BET
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.TIPS,
                        NatsGamblingTransactionType.TYPE_TIPS
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                )
        );
    }

    /**
     * @param betAmountParam Сумма ставки.
     * @param operationParam Тип операции ставки (для запроса и проверки NATS).
     */
    @ParameterizedTest(name = "тип = {1}, сумма = {0}")
    @MethodSource("betProvider")
    @DisplayName("Изменение остатка TurnoverLimit при совершении ставки:")
    void testTurnoverLimitChangeOnBet(
            BigDecimal betAmountParam,
            NatsGamblingTransactionOperation operationParam
    ) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody betRequestBody;
            NatsMessage<NatsGamblingEventPayload> betEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal limitAmount;
            BigDecimal expectedRestAmountAfterBet;
            BigDecimal expectedSpentAmountAfterBet;
            BigDecimal expectedPlayerBalanceAfterBet;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount = limitAmountBase;
        ctx.expectedSpentAmountAfterBet = betAmountParam;
        ctx.expectedRestAmountAfterBet = ctx.limitAmount.subtract(betAmountParam);
        ctx.expectedPlayerBalanceAfterBet = initialAdjustmentAmount.subtract(betAmountParam);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(ctx.limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setTurnoverLimit(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");

            step("Sub-step NATS: получение события limit_changed_v2", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                        NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader) &&
                                NatsLimitType.TURNOVER_FUNDS.getValue().equals(payload.getLimits().get(0).getLimitType());

                ctx.limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();

                assertNotNull(ctx.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Совершение ставки", () -> {
            ctx.betRequestBody = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.betRequestBody),
                    ctx.betRequestBody);

            assertAll("manager_api.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(ctx.betRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedPlayerBalanceAfterBet.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события betted_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.betRequestBody.getTransactionId().equals(payload.getUuid());

                ctx.betEvent = natsClient.findMessageAsync(
                        subject,
                        NatsGamblingEventPayload.class,
                        filter).get();

                assertNotNull(ctx.betEvent, "nats.betted_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.betEvent.getSequence());

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) ctx.betEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet.limits"),
                    () -> {
                        var turnoverLimitOpt = aggregate.getLimits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, ctx.expectedRestAmountAfterBet.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, ctx.expectedSpentAmountAfterBet.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, ctx.limitAmount.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}