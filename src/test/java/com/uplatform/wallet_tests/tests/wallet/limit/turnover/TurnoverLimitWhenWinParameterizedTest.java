package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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

@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class TurnoverLimitWhenWinParameterizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);

    static Stream<Arguments> winProvider() {
        return Stream.of(
                Arguments.of(
                        generateBigDecimalAmount(limitAmountBase),
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(limitAmountBase),
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        generateBigDecimalAmount(limitAmountBase),
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.WIN,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.JACKPOT,
                        NatsGamblingTransactionType.TYPE_WIN
                ),
                Arguments.of(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN,
                        NatsGamblingTransactionType.TYPE_FREESPIN
                )
        );
    }

    @ParameterizedTest(name = "тип = {1}, сумма = {0}")
    @MethodSource("winProvider")
    @DisplayName("Отсутствие изменения остатка TurnoverLimit при получении выигрыша:")
    void testTurnoverLimitUnchangedOnWin(
            BigDecimal winAmountParam,
            NatsGamblingTransactionOperation operationParam
    ) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext extends BaseParameterizedTest {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            WinRequestBody winRequestBody;
            NatsMessage<NatsGamblingEventPayload> winEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal limitAmount;
            BigDecimal expectedRestAmountAfterWin;
            BigDecimal expectedSpentAmountAfterWin;
            BigDecimal expectedPlayerBalanceAfterWin;
        }
        final TestContext ctx = new TestContext();

        ctx.limitAmount = limitAmountBase;
        ctx.expectedSpentAmountAfterWin = BigDecimal.ZERO;
        ctx.expectedRestAmountAfterWin = ctx.limitAmount;
        ctx.expectedPlayerBalanceAfterWin = initialAdjustmentAmount.add(winAmountParam);

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
                                payload.getLimits() != null && !payload.getLimits().isEmpty() &&
                                NatsLimitType.TURNOVER_FUNDS.getValue().equals(payload.getLimits().get(0).getLimitType());

                ctx.limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();
                assertNotNull(ctx.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Начисление выигрыша", () -> {
            ctx.winRequestBody = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.winRequestBody),
                    ctx.winRequestBody);

            assertAll("manager_api.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(ctx.winRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                    () -> assertEquals(0, ctx.expectedPlayerBalanceAfterWin.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события won_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                        ctx.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                ctx.winRequestBody.getTransactionId().equals(payload.getUuid());

                ctx.winEvent = natsClient.findMessageAsync(
                        subject,
                        NatsGamblingEventPayload.class,
                        filter).get();
                assertNotNull(ctx.winEvent, "nats.won_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.winEvent.getSequence());

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) ctx.winEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet.limits"),
                    () -> {
                        var turnoverLimitOpt = aggregate.getLimits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, ctx.expectedRestAmountAfterWin.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, ctx.expectedSpentAmountAfterWin.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, ctx.limitAmount.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}