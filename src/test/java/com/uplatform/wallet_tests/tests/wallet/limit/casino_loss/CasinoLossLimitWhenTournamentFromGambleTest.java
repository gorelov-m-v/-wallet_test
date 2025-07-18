package com.uplatform.wallet_tests.tests.wallet.limit.casino_loss;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Limits")
@Feature("CasinoLossLimit")
@Suite("Позитивные сценарии: CasinoLossLimit")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class CasinoLossLimitWhenTournamentFromGambleTest extends BaseTest {

    @Test
    @DisplayName("Изменение остатка CasinoLossLimit при получении турнирного выигрыша в казино")
    void shouldRejectBetWhenGamblingDisabled() {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody tournamentRequestBody;
            NatsMessage<NatsGamblingEventPayload> tournamentEvent;
            BigDecimal limitAmount;
            BigDecimal tournamentAmount;
            BigDecimal expectedRestAmountAfterTournament;
            BigDecimal expectedSpentAmountAfterTournament;
        }
        final TestData testData = new TestData();

        testData.limitAmount =  new BigDecimal("150.12");
        testData.tournamentAmount = new BigDecimal("10.15");
        testData.expectedSpentAmountAfterTournament = testData.tournamentAmount.negate();
        testData.expectedRestAmountAfterTournament = testData.limitAmount.add(testData.tournamentAmount);

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

                assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
            });
        });

        step("Manager API: Получение выигрыша в турнире", () -> {
            testData.tournamentRequestBody = TournamentRequestBody.builder()
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(testData.tournamentAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .roundId(UUID.randomUUID().toString())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, testData.tournamentRequestBody),
                    testData.tournamentRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.status_code");

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

                assertNotNull(testData.tournamentEvent, "nats.event.tournament_started");
            });
        });

        step("Redis(Wallet): Проверка изменений лимита в агрегате", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.tournamentEvent.getSequence());

            assertAll(
                    () -> assertEquals(0, testData.expectedRestAmountAfterTournament.compareTo(aggregate.getLimits().get(0).getRest()), "redis.wallet.limit.rest"),
                    () -> assertEquals(0, testData.expectedSpentAmountAfterTournament.compareTo(aggregate.getLimits().get(0).getSpent()), "redis.wallet.limit.spent"),
                    () -> assertEquals(0, testData.limitAmount.compareTo(aggregate.getLimits().get(0).getAmount()), "redis.wallet.limit.amount")
            );
        });
    }
}