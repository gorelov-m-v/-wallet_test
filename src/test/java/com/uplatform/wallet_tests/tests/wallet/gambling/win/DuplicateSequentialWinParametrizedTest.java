package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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
 * Интеграционный параметризованный тест, проверяющий API ответ при последовательной отправке двух идентичных запросов на выигрыш
 * для различных типов операций (WIN, FREESPIN, JACKPOT) и различных сумм (включая нулевую).
 * Ожидается, что первый запрос будет успешным, а второй, идентичный ему, вернет ответ 200 OK с тем же transactionId и нулевым балансом,
 * подтверждая идемпотентность операции.
 *
 * <p><b>Цель теста:</b></p>
 * <p>Убедиться, что API Manager корректно обрабатывает попытку дублирования выигрыша для каждого типа операции и суммы,
 * когда второй запрос полностью идентичен первому (включая {@code transactionId}).
 * Тест ожидает, что система вернет ответ {@link HttpStatus#OK} с телом {@link GamblingResponseBody},
 * содержащим тот же {@code transactionId} и нулевой баланс, что является подтверждением корректной идемпотентной обработки дубликата.</p>
 *
 * <p><b>Сценарий теста (для каждой комбинации типа операции и суммы):</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> Создается новый игрок с начальным балансом.</li>
 *   <li><b>Создание игровой сессии:</b> Инициируется игровая сессия для зарегистрированного игрока.</li>
 *   <li><b>Совершение базовой ставки:</b> Делается одна ставка, к которой будет привязан выигрыш.</li>
 *   <li><b>Совершение первого (успешного) выигрыша:</b> Через API Manager отправляется запрос {@code /win} с указанным типом операции и суммой.
 *       Проверяется успешный ответ (HTTP 200 OK). Параметры этого запроса сохраняются.</li>
 *   <li><b>Ожидание NATS-события:</b> Ожидается NATS-событие {@code won_from_gamble} для подтверждения обработки первого выигрыша.</li>
 *   <li><b>Попытка дублирования выигрыша:</b> Через API отправляется второй запрос {@code /win}
 *       с абсолютно теми же параметрами, что и первый успешный выигрыш (включая {@code transactionId}, тип операции и сумму).</li>
 *   <li><b>Проверка ответа API на дубликат:</b> Ожидается, что API вернет успешный ответ со статусом {@link HttpStatus#OK}.
 *       Тело ответа ({@link GamblingResponseBody}) должно содержать {@code transactionId} из первого запроса и баланс,
 *       равный {@link BigDecimal#ZERO}, что подтверждает корректную обработку дублирующей транзакции.</li>
 * </ol>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class DuplicateSequentialWinParametrizedTest extends BaseParameterizedTest {


    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("100.00");
    private static final BigDecimal betAmount = new BigDecimal("10.00");
    private static final BigDecimal winAmount = new BigDecimal("1.00");

    static Stream<Arguments> winOperationAndAmountProvider() {
        return Stream.of(
                Arguments.of(NatsGamblingTransactionOperation.WIN, winAmount),
                Arguments.of(NatsGamblingTransactionOperation.WIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, winAmount),
                Arguments.of(NatsGamblingTransactionOperation.FREESPIN, BigDecimal.ZERO),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, winAmount),
                Arguments.of(NatsGamblingTransactionOperation.JACKPOT, BigDecimal.ZERO)
        );
    }

    @ParameterizedTest(name = "тип операции = {0}, сумма = {1}")
    @MethodSource("winOperationAndAmountProvider")
    @DisplayName("Дублирование выигрыша при последовательной отправке")
    void testDuplicateWinReturnsIdempotentResponse(NatsGamblingTransactionOperation operationParam, BigDecimal winAmountParam)  {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            BetRequestBody initialBetRequest;
            WinRequestBody firstWinRequest;
            NatsMessage<NatsGamblingEventPayload> firstWinNatsEvent;
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

        step("Manager API: Совершение базовой ставки", () -> {
            ctx.initialBetRequest = BetRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, ctx.initialBetRequest),
                    ctx.initialBetRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.base_bet_status_code");
        });

        step("Manager API: Совершение первого выигрыша", () -> {
            ctx.firstWinRequest = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(operationParam)
                    .roundId(ctx.initialBetRequest.getRoundId())
                    .roundClosed(false)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.firstWinRequest),
                    ctx.firstWinRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.first_win_status_code");
        });

        step("NATS: Ожидание NATS-события won_from_gamble для первого выигрыша", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsGamblingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.WON_FROM_GAMBLE.getHeaderValue().equals(typeHeader) &&
                            ctx.firstWinRequest.getTransactionId().equals(payload.getUuid());

            ctx.firstWinNatsEvent = natsClient.findMessageAsync(
                    subject,
                    NatsGamblingEventPayload.class,
                    filter).get();

            assertNotNull(ctx.firstWinNatsEvent, "nats.won_from_gamble");
        });

        step("Manager API: Попытка дублирования выигрыша", () -> {
            var duplicateResponse = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.firstWinRequest),
                    ctx.firstWinRequest
            );

            var responseBody = duplicateResponse.getBody();

            assertAll("Проверка ответа на дубликат выигрыша",
                    () -> assertEquals(HttpStatus.OK, duplicateResponse.getStatusCode(), "manager_api.win.duplicate_win_status_code"),
                    () -> assertNotNull(responseBody, "manager_api.win.duplicate_win_response_body"),
                    () -> assertEquals(ctx.firstWinRequest.getTransactionId(), responseBody.getTransactionId(), "manager_api.win.duplicate_win_transaction_id"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(responseBody.getBalance()), "manager_api.win.duplicate_win_balance")
            );
        });
    }
}