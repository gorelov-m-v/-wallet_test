package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiPredicate;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест, проверяющий API ответ при последовательной отправке двух идентичных запросов на турнирный выигрыш.
 * Ожидается, что первый запрос будет успешным, а второй вернет ошибку валидации из-за дублирования transactionId.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager корректно обрабатывает попытку дублирования турнирного выигрыша,
 * когда второй запрос полностью идентичен первому (включая {@code transactionId}).
 * Тест ожидает, что система вернет ошибку валидации (например, {@link GamblingErrors#VALIDATION_ERROR})
 * при второй попытке.</p>
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок с начальным балансом.</li>
 *   <li><b>Создание игровой сессии:</b> Инициируется игровая сессия для зарегистрированного игрока.</li>
 *   <li><b>Совершение первого (успешного) турнирного выигрыша:</b> Через API Manager отправляется запрос {@code /tournament}.
 *       Проверяется успешный ответ (HTTP 200 OK). Параметры этого запроса сохраняются.</li>
 *   <li><b>Ожидание NATS-события:</b> Ожидается NATS-событие {@code tournament_won_from_gamble} для подтверждения обработки первого выигрыша.</li>
 *   <li><b>Попытка дублирования турнирного выигрыша:</b> Через API отправляется второй запрос {@code /tournament}
 *       с абсолютно теми же параметрами, что и первый успешный выигрыш (включая {@code transactionId}).</li>
 *   <li><b>Проверка ответа API на дубликат:</b> Ожидается, что API вернет ошибку {@link FeignException}
 *       со статусом {@link HttpStatus#BAD_REQUEST} и кодом ошибки {@link GamblingErrors#VALIDATION_ERROR}.</li>
 * </ol>
 */
@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Негативные сценарии: /tournament")
@Tag("Gambling") @Tag("Wallet") @Tag("Idempotency")
@TmsLink("")
class DuplicateSequentialTournamentWinTest {

    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private TestUtils utils;
    @Autowired private NatsClient natsClient;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("1000.00");
    private static final BigDecimal singleTournamentAmount = new BigDecimal("50.00");

    @Test
    @DisplayName("Дублирование турнирного выигрыша при последовательной отправке идентичных запросов (ожидается ошибка валидации)")
    void testDuplicateSequentialTournamentWinExpectingValidationError()  {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            TournamentRequestBody firstTournamentRequest;
            NatsMessage<NatsGamblingEventPayload> firstTournamentNatsEvent;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            testData.gameLaunchData = defaultTestSteps.createGameSession(testData.registeredPlayer);
            assertNotNull(testData.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение первого (успешного) турнирного выигрыша", () -> {
            testData.firstTournamentRequest = TournamentRequestBody.builder()
                    .playerId(testData.registeredPlayer.getWalletData().getWalletUUID())
                    .sessionToken(testData.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(singleTournamentAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .roundId(UUID.randomUUID().toString())
                    .gameUuid(testData.gameLaunchData.getDbGameSession().getGameUuid())
                    .providerUuid(testData.gameLaunchData.getDbGameSession().getProviderUuid())
                    .build();

            var response = managerClient.tournament(
                    casinoId,
                    utils.createSignature(ApiEndpoints.TOURNAMENT, testData.firstTournamentRequest),
                    testData.firstTournamentRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.tournament.first_win_status_code");
        });

        step("NATS: Ожидание NATS-события tournament_won_from_gamble для первого турнирного выигрыша", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.TOURNAMENT_WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            testData.firstTournamentRequest.getTransactionId().equals(payload.getUuid());

            testData.firstTournamentNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(testData.firstTournamentNatsEvent, "nats.tournament_won_from_gamble_event_for_first_win");
        });

        step("Manager API: Попытка дублирования турнирного выигрыша (повторная отправка с ID: " + testData.firstTournamentRequest.getTransactionId() + ") и проверка ошибки", () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.tournament(
                            casinoId,
                            utils.createSignature(ApiEndpoints.TOURNAMENT, testData.firstTournamentRequest),
                            testData.firstTournamentRequest
                    ),
                    "manager_api.tournament.feign_exception_expected_on_duplicate"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки дублирования турнирного выигрыша",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.error.status_code_on_duplicate"),
                    () -> assertNotNull(error, "manager_api.error.parsed_object_on_duplicate"),
                    () -> assertEquals(GamblingErrors.VALIDATION_ERROR.getCode(), error.getCode(), "manager_api.error.code_on_duplicate"),
                    () -> assertNotNull(error.getMessage(), "manager_api.error.message_on_duplicate")
            );
        });
    }
}