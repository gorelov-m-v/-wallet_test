package com.uplatform.wallet_tests.tests.wallet.limit.turnover;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitType;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий, что лимит на оборот средств в агрегате игрока
 * НЕ изменяется при получении турнирных выигрышей в гемблинге.
 *
 * <p>Тест охватывает различные суммы турнирных выигрышей, включая ненулевые и нулевые.
 * Каждая итерация параметризованного теста выполняется с полностью изолированным состоянием,
 * включая создание нового игрока, игровой сессии и установку лимита на оборот.</p>
 *
 * <p><b>Проверяемые уровни приложения:</b></p>
 * <ul>
 *   <li>Public API: Установка лимита на оборот через FAPI ({@code /profile/limit/turnover}).</li>
 *   <li>REST API: Начисление турнирного выигрыша через Manager API ({@code /tournament}).</li>
 *   <li>Система обмена сообщениями:
 *     <ul>
 *       <li>Передача события {@code limit_changed_v2} через NATS при установке лимита.</li>
 *       <li>Передача события {@code tournament_won_from_gamble} через NATS при начислении турнирного выигрыша.</li>
 *     </ul>
 *   </li>
 *   <li>Кэш: Проверка отсутствия изменений в данных лимита оборота и обновление баланса игрока в агрегате кошелька в Redis (ключ {@code wallet:<wallet_uuid>}).</li>
 * </ul>
 *
 * <p><b>Проверяемые суммы турнирных выигрышей:</b></p>
 * <ul>
 *   <li>Динамически генерируемые ненулевые значения.</li>
 *   <li>Нулевые значения ({@code 0.00}).</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("TurnoverLimit")
@Suite("Позитивные сценарии: TurnoverLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits") @Tag("Tournament")
class TurnoverLimitWhenTournamentTest extends BaseParameterizedTest {
    @Autowired private FapiClient publicClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private NatsClient natsClient;
    @Autowired private WalletRedisClient redisClient;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal limitAmountBase = generateBigDecimalAmount(initialAdjustmentAmount);

    static Stream<Arguments> tournamentAmountProvider() {
        return Stream.of(
                Arguments.of(generateBigDecimalAmount(limitAmountBase)),
                Arguments.of(BigDecimal.ZERO)
        );
    }

    /**
     * @param tournamentAmountParam Сумма турнирного выигрыша.
     */
    @ParameterizedTest(name = "сумма выигрыша = {0}")
    @MethodSource("tournamentAmountProvider")
    @DisplayName("Отсутствие изменения остатка TurnoverLimit при получении турнирного выигрыша:")
    void testTurnoverLimitUnchangedOnTournamentWin(
            BigDecimal tournamentAmountParam
    ) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequestBody;
            NatsMessage<NatsGamblingEventPayload> tournamentEvent;
            NatsMessage<NatsLimitChangedV2Payload> limitCreateEvent;
            BigDecimal limitAmount;
            BigDecimal expectedRestAmountAfterTournament;
            BigDecimal expectedSpentAmountAfterTournament;
            BigDecimal expectedPlayerBalanceAfterTournament;
        }
        final TestData testData = new TestData();

        testData.limitAmount = limitAmountBase;
        testData.expectedSpentAmountAfterTournament = BigDecimal.ZERO;
        testData.expectedRestAmountAfterTournament = testData.limitAmount;
        testData.expectedPlayerBalanceAfterTournament = initialAdjustmentAmount.add(tournamentAmountParam);

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var request = SetTurnoverLimitRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(testData.limitAmount.toString())
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
                                payload.getLimits().stream().anyMatch(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()));

                testData.limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();
                assertNotNull(testData.limitCreateEvent, "nats.limit_changed_v2_event");
            });
        });

        step("Manager API: Начисление турнирного выигрыша", () -> {
            testData.tournamentRequestBody = TournamentRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(tournamentAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .roundId(UUID.randomUUID().toString())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, testData.tournamentRequestBody),
                    testData.tournamentRequestBody);

            assertAll("manager_api.response_validation",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertEquals(testData.tournamentRequestBody.getTransactionId(), response.getBody().getTransactionId(), "manager_api.body.transactionId"),
                    () -> assertEquals(0, testData.expectedPlayerBalanceAfterTournament.compareTo(response.getBody().getBalance()), "manager_api.body.balance")
            );

            step("Sub-step NATS: Проверка поступления события tournament_won_from_gamble", () -> {
                var subject = natsClient.buildWalletSubject(
                        testData.registeredPlayer.getWalletData().getPlayerUUID(),
                        testData.registeredPlayer.getWalletData().getWalletUUID());

                BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                        NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                                testData.tournamentRequestBody.getTransactionId().equals(payload.getUuid());

                testData.tournamentEvent = natsClient.findMessageAsync(
                        subject,
                        NatsGamblingEventPayload.class,
                        filter).get();
                assertNotNull(testData.tournamentEvent, "nats.tournament_won_from_gamble");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита и баланса в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.tournamentEvent.getSequence());

            assertAll("redis.wallet.limit_data_validation",
                    () -> assertEquals((int) testData.tournamentEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.wallet.last_seq_number"),
                    () -> assertFalse(aggregate.getLimits().isEmpty(), "redis.wallet.limits"),
                    () -> {
                        var turnoverLimitOpt = aggregate.getLimits().stream()
                                .filter(l -> NatsLimitType.TURNOVER_FUNDS.getValue().equals(l.getLimitType()) &&
                                        NatsLimitIntervalType.DAILY.getValue().equals(l.getIntervalType()))
                                .findFirst();
                        assertTrue(turnoverLimitOpt.isPresent(), "redis.wallet.turnover_limit");
                        var turnoverLimit = turnoverLimitOpt.get();

                        assertEquals(0, testData.expectedRestAmountAfterTournament.compareTo(turnoverLimit.getRest()), "redis.wallet.limit.rest");
                        assertEquals(0, testData.expectedSpentAmountAfterTournament.compareTo(turnoverLimit.getSpent()), "redis.wallet.limit.spent");
                        assertEquals(0, testData.limitAmount.compareTo(turnoverLimit.getAmount()), "redis.wallet.limit.amount");
                    }
            );
        });
    }
}