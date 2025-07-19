package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.NatsGamblingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный параметризованный тест, проверяющий API ответ при последовательной отправке двух идентичных запросов на ставку
 * для различных типов операций (BET, TIPS, FREESPIN) и различных сумм (включая нулевую).
 * Ожидается, что первый запрос будет успешным, а второй, идентичный ему, вернет ответ 200 OK с тем же transactionId и нулевым балансом,
 * подтверждая идемпотентность операции.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager корректно обрабатывает попытку дублирования ставки для каждого типа операции и суммы,
 * когда второй запрос полностью идентичен первому (включая {@code transactionId}).
 * Тест ожидает, что система вернет ответ {@link HttpStatus#OK} с телом {@link com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody},
 * содержащим тот же {@code transactionId} и нулевой баланс, что является подтверждением корректной идемпотентной обработки дубликата.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации типа операции и суммы):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок с начальным балансом.</li>
 *   <li><b>Создание игровой сессии:</b> Инициируется игровая сессия для зарегистрированного игрока.</li>
 *   <li><b>Совершение первой (успешной) ставки:</b> Через API Manager отправляется запрос {@code /bet} с указанным типом операции и суммой.
 *       Проверяется успешный ответ (HTTP 200 OK). Параметры этого запроса сохраняются.</li>
 *   <li><b>Ожидание NATS-события:</b> Ожидается NATS-событие {@code betted_from_gamble} для подтверждения обработки первой ставки.</li>
 *   <li><b>Попытка дублирования ставки:</b> Через API отправляется второй запрос {@code /bet}
 *       с абсолютно теми же параметрами, что и первая успешная ставка (включая {@code transactionId}, тип операции и сумму).</li>
 *   <li><b>Проверка ответа API на дубликат:</b> Ожидается, что API вернет успешный ответ со статусом {@link HttpStatus#OK}.
 *       Тело ответа ({@link com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody}) должно содержать {@code transactionId} из первого запроса и баланс,
 *       равный {@link BigDecimal#ZERO}, что подтверждает корректную обработку дублирующей транзакции.</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class DuplicateSequentialBetParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("100.00");
    private static final BigDecimal defaultBetAmount = new BigDecimal("10.00");

    static Stream<Arguments> betOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.BET, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.BET, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.TIPS, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, defaultBetAmount),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO)
        );
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("betOperationAndAmountProvider")
    @DisplayName("Дублирование ставки при последовательной отправке")
    void testDuplicateBetReturnsIdempotentResponse(NatsGamblingTransactionOperation operationParam, BigDecimal betAmountParam)  {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody firstBetRequest;
            NatsMessage<NatsGamblingEventPayload> firstBetNatsEvent;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение первой ставки", () -> {
            ctx.firstBetRequest = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.firstBetRequest),
                    ctx.firstBetRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.first_bet_status_code");
        });

        step("NATS: Ожидание NATS-события betted_from_gamble для первой ставки", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.firstBetRequest.getTransactionId().equals(payload.getUuid());

            ctx.firstBetNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(ctx.firstBetNatsEvent, "nats.betted_from_gamble");
        });

        step("Manager API: Попытка дублирования ставки", () -> {
            var duplicateResponse = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.firstBetRequest),
                    ctx.firstBetRequest
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка ответа на дубликат ставки",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.bet.duplicate_bet_status_code"),
                    () -> assertNotNull(responseBody, "manager_api.bet.duplicate_bet_response_body"),
                    () -> assertEquals(ctx.firstBetRequest.getTransactionId(), responseBody.getTransactionId(), "manager_api.bet.duplicate_bet_transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.getBalance()), "manager_api.bet.duplicate_bet_balance")
            );
        });
    }
}