package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий невозможность выполнения роллбэка для транзакций начисления выигрышей различных типов,
 * включая транзакции с нулевой суммой выигрыша.
 *
 * <p>Данный параметризованный тест проверяет поведение системы при попытке выполнить операцию роллбэка,
 * указывая в качестве {@code rollbackTransactionId} идентификатор транзакции начисления выигрыша
 * одного из следующих типов: {@link NatsGamblingTransactionOperation#WIN WIN},
 * {@link NatsGamblingTransactionOperation#JACKPOT JACKPOT}, или {@link NatsGamblingTransactionOperation#FREESPIN FREESPIN}.
 * Тест подтверждает, что система корректно обрабатывает такие запросы, отклоняя их,
 * поскольку операция роллбэка применима только к транзакциям типа "ставка" (bet).</p>
 *
 * <p>Тест также включает сценарии с нулевой суммой выигрыша. Ключевым аспектом является то, что роллбэк применим исключительно
 * к транзакциям типа "ставка" (bet), а не к результатам этих ставок (выигрышам), независимо от их суммы.</p>
 *
 * <p><b>Последовательность действий для каждого набора параметров:</b></p>
 * <ol>
 *   <li>Регистрация игрока с начальным балансом.</li>
 *   <li>Создание игровой сессии.</li>
 *   <li>Совершение транзакции начисления выигрыша указанного типа ({@code win}, {@code jackpot}, {@code freespin})
 *       и суммы (включая нулевую) игроку (успешно).</li>
 *   <li>Попытка выполнения роллбэка, используя {@code transactionId} транзакции выигрыша
 *       в качестве {@code rollbackTransactionId} (ожидается ошибка).</li>
 * </ol>
 *
 * <p><b>Ожидаемые результаты для каждого набора параметров:</b></p>
 * <ul>
 *   <li>Начисление выигрыша (в том числе с нулевой суммой) выполняется успешно (HTTP 200 OK).</li>
 *   <li>Попытка роллбэка для транзакции выигрыша (любого из тестируемых типов и сумм)
 *       должна быть отклонена с кодом {@code HTTP 400 BAD REQUEST}
 *       и содержать ошибку {@link GamblingErrors#ROLLBACK_NOT_ALLOWED} (или аналогичную ошибку,
 *       указывающую, что исходная транзакция не является ставкой, для которой возможен роллбэк).</li>
 * </ul>
 *
 * <p><b>Тестируемые типы транзакций выигрыша ({@link NatsGamblingTransactionOperation}):</b></p>
 * <ul>
 *   <li>{@code WIN} - Обычный выигрыш.</li>
 *   <li>{@code JACKPOT} - Выигрыш джекпота.</li>
 *   <li>{@code FREESPIN} - Выигрыш, полученный в результате бесплатных вращений.</li>
 * </ul>
 *
 * @see NatsGamblingTransactionOperation
 * @see GamblingErrors#ROLLBACK_NOT_ALLOWED
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackAfterWinParametrizedTest extends BaseParameterizedTest {

    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("150.00");

    static Stream<Arguments> winLikeTransactionTypeProvider() {
        return Stream.of(
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.WIN
                ),
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.JACKPOT
                ),
                arguments(
                        generateBigDecimalAmount(initialAdjustmentAmount),
                        NatsGamblingTransactionOperation.FREESPIN
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.WIN
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.JACKPOT
                ),
                arguments(
                        BigDecimal.ZERO,
                        NatsGamblingTransactionOperation.FREESPIN
                )
        );
    }

    /**
     * Тестирует невозможность выполнения роллбэка для транзакции выигрыша.
     *
     * @param winAmountParam Сумма исходной транзакции выигрыша (может быть 0).
     * @param winOperationTypeParam Тип операции выигрыша (WIN, JACKPOT, FREESPIN).
     */
    @ParameterizedTest(name = "транзакция типа [{1}] суммой [{0}] должна вызвать ошибку")
    @MethodSource("winLikeTransactionTypeProvider")
    @DisplayName("Попытка роллбэка:")
    void testRollbackForWinTransactionReturnsError(BigDecimal winAmountParam, NatsGamblingTransactionOperation winOperationTypeParam) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            GameLaunchData gameLaunchData;
            WinRequestBody winRequestBody;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            ctx.gameLaunchData = defaultTestSteps.createGameSession(ctx.registeredPlayer);
            assertNotNull(ctx.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение исходной транзакции выигрыша", () -> {
            ctx.winRequestBody = WinRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .type(winOperationTypeParam)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, ctx.winRequestBody),
                    ctx.winRequestBody);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code");
        });

        step("Manager API: Попытка выполнения роллбэка для транзакции выигрыша", () -> {
            var rollbackRequestBody = RollbackRequestBody.builder()
                    .sessionToken(ctx.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmountParam)
                    .transactionId(UUID.randomUUID().toString())
                    .rollbackTransactionId(ctx.winRequestBody.getTransactionId())
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .playerId(ctx.registeredPlayer.getWalletData().getWalletUUID())
                    .gameUuid(ctx.gameLaunchData.getDbGameSession().getGameUuid())
                    .roundId(ctx.winRequestBody.getRoundId())
                    .roundClosed(true)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.rollback(
                            casinoId,
                            utils.createSignature(ApiEndpoints.ROLLBACK, rollbackRequestBody),
                            rollbackRequestBody
                    ),
                    "manager_api.rollback_after_win.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки при попытке роллбэка после транзакции выигрыша",
                    () -> assertEquals(HttpStatus.BAD_REQUEST.value(), thrownException.status(), "manager_api.rollback.status_code"),
                    () -> assertNotNull(error, "manager_api.rollback.body"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getCode(), error.getCode(), "manager_api.rollback.error_code"),
                    () -> assertEquals(GamblingErrors.ROLLBACK_NOT_ALLOWED.getMessage(), error.getMessage(), "manager_api.rollback.error_message")
            );
        });
    }
}