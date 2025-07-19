package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors.*;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий обработку некорректных запросов на совершение ставок в системе Wallet.
 *
 * <p>Данный параметризованный тест проверяет валидацию входных данных запроса и бизнес-ограничения
 * при обработке запросов на совершение ставки в азартных играх. Тест проверяет корректность
 * возвращаемых кодов ошибок и сообщений при различных некорректных запросах.</p>
 *
 * <p><b>Проверяемые типы ошибок:</b></p>
 * <ul>
 *   <li>Отсутствие обязательных полей тела запроса (sessionToken, transactionId, type, roundId).</li>
 *   <li>Валидация форматов и ограничений полей (UUID, максимальная длина строки).</li>
 *   <li>Бизнес-ограничения (отрицательная сумма, недостаточный баланс).</li>
 * </ul>
 *
 * <p><b>Проверяемые коды ошибок ({@link GamblingErrors}):</b></p>
 * <ul>
 *   <li>{@code MISSING_TOKEN} (100) - Отсутствие токена сессии.</li>
 *   <li>{@code VALIDATION_ERROR} (103) - Ошибки валидации входных данных.</li>
 *   <li>{@code BUSINESS_LOGIC_ERROR} (104) - Ошибки бизнес-логики (недостаточный баланс и др.).</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class BetNegativeParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal initialAdjustmentAmount = new BigDecimal("20.00");
    private final BigDecimal validBetAmount = new BigDecimal("1.00");

    @BeforeAll
    void setup() {
        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.create_game_session");
        });
    }

    static Stream<Arguments> negativeBetScenariosProvider() {
        return Stream.of(
                arguments("без sessionToken",
                        (Consumer<BetRequestBody>) req -> req.setSessionToken(null),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN,
                        "missing session token"),

                arguments("пустой sessionToken",
                        (Consumer<BetRequestBody>) req -> req.setSessionToken(""),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN,
                        "missing session token"),

                arguments("отрицательный amount",
                        (Consumer<BetRequestBody>) req -> req.setAmount(new BigDecimal("-1.0")),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "validate request: amount: value [-1] must be greater or equal than [0]."),

                arguments("размер ставки превышает баланс",
                        (Consumer<BetRequestBody>) req -> req.setAmount(new BigDecimal("21.00")),
                        HttpStatus.BAD_REQUEST,
                        BUSINESS_LOGIC_ERROR,
                        "insufficient balance"),

                arguments("без transactionId",
                        (Consumer<BetRequestBody>) req -> req.setTransactionId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "transactionId: cannot be blank"),

                arguments("пустой transactionId",
                        (Consumer<BetRequestBody>) req -> req.setTransactionId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "transactionId: cannot be blank"),

                arguments("невалидный transactionId (не UUID)",
                        (Consumer<BetRequestBody>) req -> req.setTransactionId("not-a-uuid"),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "transactionId: must be a valid UUID"),

                arguments("без type",
                        (Consumer<BetRequestBody>) req -> req.setType(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "type: cannot be blank"),

                arguments("пустой type",
                        (Consumer<BetRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.EMPTY),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "type: cannot be blank"),

                arguments("невалидный type",
                        (Consumer<BetRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.UNKNOWN),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "type: must be a valid value"),

                arguments("roundId превышает 255 символов",
                        (Consumer<BetRequestBody>) req -> req.setRoundId("a".repeat(256)),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "roundId: the length must be no more than 255"),

                arguments("без roundId",
                        (Consumer<BetRequestBody>) req -> req.setRoundId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "roundId: cannot be blank"),

                arguments("пустой roundId",
                        (Consumer<BetRequestBody>) req -> req.setRoundId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR,
                        "roundId: cannot be blank")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeBetScenariosProvider")
    @DisplayName("Негативный сценарий совершения ставки в игровой сессии:")
    void test(
            String description,
            Consumer<BetRequestBody> requestModifier,
            HttpStatus expectedStatus,
            GamblingErrors expectedErrorCode,
            String expectedMessageSubstring)
    {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            BetRequestBody request;
        }
        final TestContext ctx = new TestContext();

        step("Подготовка некорректного запроса: " + description, () -> {
            ctx.request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(this.validBetAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            requestModifier.accept(ctx.request);
        });

        step("Manager API: Попытка некорректной ставки - " + description, () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.bet(
                            validCasinoId,
                            utils.createSignature(ApiEndpoints.BET, ctx.request),
                            ctx.request
                    ),
                    "manager_api.bet"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки",
                    () -> assertEquals(expectedStatus.value(), thrownException.status(), "manager_api.error.status_code"),
                    () -> assertEquals(expectedErrorCode.getCode(), error.getCode(), "manager_api.error.code"),
                    () -> assertTrue(error.getMessage().toLowerCase().contains(expectedMessageSubstring.toLowerCase()),
                            "manager_api.error.message")
            );
        });
    }
}