package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий обработку некорректных запросов на получение выигрыша в системе Wallet.
 *
 * <p>Данный параметризованный тест проверяет валидацию входных данных запроса
 * при обработке запросов на получение выигрыша в азартных играх. Тест проверяет корректность
 * возвращаемых кодов ошибок и сообщений при различных некорректных запросах.</p>
 *
 * <p><b>Проверяемые типы ошибок:</b></p>
 * <ul>
 *   <li>Отсутствие обязательных полей тела запроса (sessionToken, transactionId, type, roundId).</li>
 *   <li>Валидация форматов и ограничений полей (UUID, максимальная длина строки).</li>
 *   <li>Бизнес-ограничения (отрицательная сумма выигрыша).</li>
 * </ul>
 *
 * <p><b>Проверяемые коды ошибок ({@link GamblingErrors}):</b></p>
 * <ul>
 *   <li>{@code MISSING_TOKEN} (100) - Отсутствие токена сессии.</li>
 *   <li>{@code VALIDATION_ERROR} (103) - Ошибки валидации входных данных.</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Негативные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class WinNegativeParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal initialAdjustmentAmount = new BigDecimal("20.00");
    private final BigDecimal validWinAmount = new BigDecimal("5.00");

    @BeforeAll
    void setup() {
        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(this.initialAdjustmentAmount);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.create_game_session");
        });
    }

    static Stream<Arguments> negativeWinScenariosProvider() {
        return Stream.of(
                arguments("без sessionToken",
                        (Consumer<WinRequestBody>) req -> req.setSessionToken(null),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.MISSING_TOKEN,
                        "missing session token"
                ),
                arguments("пустой sessionToken",
                        (Consumer<WinRequestBody>) req -> req.setSessionToken(""),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.MISSING_TOKEN,
                        "missing session token"
                ),
                arguments("отрицательный amount",
                        (Consumer<WinRequestBody>) req -> req.setAmount(new BigDecimal("-1.00")),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "validate request: amount: value [-1] must be greater or equal than [0]."
                ),
                arguments("без transactionId",
                        (Consumer<WinRequestBody>) req -> req.setTransactionId(null),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "transactionId: cannot be blank"
                ),
                arguments("пустой transactionId",
                        (Consumer<WinRequestBody>) req -> req.setTransactionId(""),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "transactionId: cannot be blank"
                ),
                arguments("невалидный transactionId (not UUID)",
                        (Consumer<WinRequestBody>) req -> req.setTransactionId("invalid-uuid-format"),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "transactionId: must be a valid UUID"
                ),
                arguments("без type",
                        (Consumer<WinRequestBody>) req -> req.setType(null),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "type: cannot be blank"
                ),
                arguments("пустой type (enum EMPTY)",
                        (Consumer<WinRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.EMPTY),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "type: cannot be blank"
                ),
                arguments("невалидный type (enum UNKNOWN)",
                        (Consumer<WinRequestBody>) req -> req.setType(NatsGamblingTransactionOperation.UNKNOWN),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "type: must be a valid value"
                ),
                arguments("roundId превышает 255 символов",
                        (Consumer<WinRequestBody>) req -> req.setRoundId("x".repeat(256)),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "roundId: the length must be no more than 255"
                ),
                arguments("без roundId",
                        (Consumer<WinRequestBody>) req -> req.setRoundId(null),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "roundId: cannot be blank"
                ),
                arguments("пустой roundId",
                        (Consumer<WinRequestBody>) req -> req.setRoundId(""),
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.VALIDATION_ERROR,
                        "roundId: cannot be blank"
                )
        );
    }

    /**
     * @param description Описание тестового сценария для отчетности
     * @param requestModifier Функция, модифицирующая стандартный запрос для создания ошибочной ситуации
     * @param expectedStatus Ожидаемый HTTP статус-код ответа
     * @param expectedErrorCode Ожидаемый код ошибки в ответе API
     * @param expectedMessageSubstring Ожидаемая подстрока в сообщении об ошибке
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeWinScenariosProvider")
    @DisplayName("Валидация полей тела запроса на получение выигрыша:")
    void test(
            String description,
            Consumer<WinRequestBody> requestModifier,
            HttpStatus expectedStatus,
            GamblingErrors expectedErrorCode,
            String expectedMessageSubstring
    ) {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        final class TestContext {
            WinRequestBody request;
        }
        final TestContext ctx = new TestContext();

        step("Подготовка некорректного запроса: " + description, () -> {
            ctx.request = WinRequestBody.builder()
                    .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(this.validWinAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.WIN)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(true)
                    .build();

            requestModifier.accept(ctx.request);
        });

        step("Manager API: Отправка некорректного запроса и проверка ошибки", () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.win(
                            validCasinoId,
                            utils.createSignature(ApiEndpoints.WIN, ctx.request),
                            ctx.request
                    ),
                    "manager_api.win.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки",
                    () -> assertEquals(expectedStatus.value(), thrownException.status(), "manager_api.error.status_code"),
                    () -> assertEquals(expectedErrorCode.getCode(), error.getCode(), "manager_api.error.code"),
                    () -> assertTrue(error.getMessage().contains(expectedMessageSubstring), "manager_api.error.message")
            );
        });
    }
}