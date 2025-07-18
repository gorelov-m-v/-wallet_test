package com.uplatform.wallet_tests.tests.wallet.gambling.refund;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors.MISSING_TOKEN;
import static com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors.VALIDATION_ERROR;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий обработку невалидных запросов на рефанд в системе Wallet для казино.
 *
 * <p>Данный параметризованный тест проверяет корректность обработки различных некорректных запросов
 * на рефанд ставки в казино. Тест проверяет валидацию полей тела запроса, граничные значения и некорректные
 * входные данные. Для каждого негативного сценария проверяется корректность кода ошибки и соответствие
 * текста сообщения об ошибке ожидаемому.</p>
 *
 * <p>Тест использует общую подготовку: регистрацию игрока с балансом, создание игровой сессии и
 * выполнение предварительной ставки, которую затем пытается рефандить с различными некорректными параметрами.</p>
 *
 * <p><b>Проверяемые негативные сценарии:</b></p>
 * <ul>
 *   <li>Отсутствие или пустой sessionToken</li>
 *   <li>Отрицательная сумма рефанда</li>
 *   <li>Отсутствие, пустой или невалидный transactionId</li>
 *   <li>Отсутствие, пустой или невалидный betTransactionId</li>
 *   <li>Отсутствие, пустой или слишком длинный roundId</li>
 * </ul>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/refund")
@Suite("Негативные сценарии: /refund")
@Tag("Gambling") @Tag("Wallet")
class RefundNegativeParametrizedTest extends BaseParameterizedTest {
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private BetRequestBody precedingBetRequestBody;
    private static final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");
    private static final BigDecimal betAmount = generateBigDecimalAmount(initialAdjustmentAmount);

    @BeforeAll
    void setup() {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.game_session");
        });

        step("Manager API: Совершение ставки для последующего рефанда", () -> {
            var betRequest = BetRequestBody.builder()
                    .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    validCasinoId,
                    utils.createSignature(ApiEndpoints.BET, betRequest),
                    betRequest);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
            this.precedingBetRequestBody = betRequest;
        });
    }

    static Stream<Arguments> negativeRefundScenariosProvider() {
        return Stream.of(
                arguments("без sessionToken",
                        (Consumer<RefundRequestBody>) req -> req.setSessionToken(null),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN.getCode(),
                        "missing session token"),
                arguments("пустой sessionToken",
                        (Consumer<RefundRequestBody>) req -> req.setSessionToken(""),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN.getCode(),
                        "missing session token"),
                arguments("отрицательный amount",
                        (Consumer<RefundRequestBody>) req -> req.setAmount(new BigDecimal("-1.0")),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "validate request: amount: must be no less than 0."),
                arguments("без transactionId",
                        (Consumer<RefundRequestBody>) req -> req.setTransactionId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "transactionId: cannot be blank"),
                arguments("пустой transactionId",
                        (Consumer<RefundRequestBody>) req -> req.setTransactionId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "transactionId: cannot be blank"),
                arguments("невалидный transactionId (не UUID)",
                        (Consumer<RefundRequestBody>) req -> req.setTransactionId("not-a-uuid"),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "transactionId: must be a valid UUID"),
                arguments("без betTransactionId",
                        (Consumer<RefundRequestBody>) req -> req.setBetTransactionId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "betTransactionId: cannot be blank"),
                arguments("пустой betTransactionId",
                        (Consumer<RefundRequestBody>) req -> req.setBetTransactionId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "betTransactionId: cannot be blank"),
                arguments("невалидный betTransactionId (не UUID)",
                        (Consumer<RefundRequestBody>) req -> req.setBetTransactionId("not-a-uuid"),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "betTransactionId: must be a valid UUID"),
                arguments("roundId превышает 255 символов",
                        (Consumer<RefundRequestBody>) req -> req.setRoundId("a".repeat(256)),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "roundId: the length must be no more than 255"),
                arguments("без roundId",
                        (Consumer<RefundRequestBody>) req -> req.setRoundId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "roundId: cannot be blank"),
                arguments("пустой roundId",
                        (Consumer<RefundRequestBody>) req -> req.setRoundId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR.getCode(),
                        "roundId: cannot be blank")
        );
    }

    /**
     * @param description Описание тестового сценария для лучшей читаемости в отчетах
     * @param requestModifier Функция-модификатор, которая изменяет запрос для имитации ошибки
     * @param expectedStatus Ожидаемый HTTP-статус ответа
     * @param expectedErrorCode Ожидаемый код ошибки в ответе
     * @param expectedMessageSubstring Ожидаемая подстрока в сообщении об ошибке
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeRefundScenariosProvider")
    @DisplayName("Негативный сценарий получения рефанда в игровой сессии:")
    void test(
            String description,
            Consumer<RefundRequestBody> requestModifier,
            HttpStatus expectedStatus,
            Integer expectedErrorCode,
            String expectedMessageSubstring)
    {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        RefundRequestBody requestBody = RefundRequestBody.builder()
                .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                .amount(betAmount)
                .transactionId(UUID.randomUUID().toString())
                .betTransactionId(this.precedingBetRequestBody.getTransactionId())
                .roundId(this.precedingBetRequestBody.getRoundId())
                .roundClosed(true)
                .playerId(this.registeredPlayer.getWalletData().getWalletUUID())
                .currency(this.registeredPlayer.getWalletData().getCurrency())
                .gameUuid(this.gameLaunchData.getDbGameSession().getGameUuid())
                .build();

        requestModifier.accept(requestBody);

        step("Manager API: Попытка некорректного рефанда", () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.refund(
                            validCasinoId,
                            utils.createSignature(ApiEndpoints.REFUND, requestBody),
                            requestBody
                    ),
                    "manager_api.refund_negative.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll(
                    () -> assertEquals(expectedStatus.value(), thrownException.status(), "manager_api.refund_negative.status_code"),
                    () -> assertEquals(expectedErrorCode, error.getCode(), "manager_api.refund_negative.error_code"),
                    () -> assertTrue(error.getMessage().toLowerCase().contains(expectedMessageSubstring.toLowerCase()), "manager_api.refund_negative.error_message")
            );
        });
    }
}