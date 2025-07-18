package com.uplatform.wallet_tests.tests.wallet.gambling.rollback;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
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

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/rollback")
@Suite("Негативные сценарии: /rollback")
@Tag("Gambling") @Tag("Wallet")
class RollbackNegativeParametrizedTest extends BaseParameterizedTest {
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal adjustmentAmount = new BigDecimal("2000.00");
    private final BigDecimal betAmount = new BigDecimal("5.00");
    private final BigDecimal validRollbackAmount = new BigDecimal("5.00");
    private BetRequestBody betRequestBody;

    @BeforeAll
    void setup() {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(this.adjustmentAmount);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.create_game_session");
        });

        step("Manager API: Совершение ставки для последующего роллбэка", () -> {
            var request = BetRequestBody.builder()
                    .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(this.betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(NatsGamblingTransactionOperation.BET)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    validCasinoId,
                    utils.createSignature(ApiEndpoints.BET, request),
                    request);

            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code");
            this.betRequestBody = request;
        });
    }

    static Stream<Arguments> negativeRollbackScenariosProvider() {
        final int VALIDATION_ERROR_CODE = 103;
        final int MISSING_TOKEN_CODE = 100;

        return Stream.of(
                arguments("без sessionToken",
                        (Consumer<RollbackRequestBody>) req -> req.setSessionToken(null),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN_CODE,
                        "missing session token"),
                arguments("пустой sessionToken",
                        (Consumer<RollbackRequestBody>) req -> req.setSessionToken(""),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN_CODE,
                        "missing session token"),
                arguments("отрицательный amount",
                        (Consumer<RollbackRequestBody>) req -> req.setAmount(new BigDecimal("-1.0")),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "validate request: amount: must be no less than 0."),
                arguments("без transactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setTransactionId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "transactionId: cannot be blank"),
                arguments("пустой transactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setTransactionId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "transactionId: cannot be blank"),
                arguments("невалидный transactionId (не UUID)",
                        (Consumer<RollbackRequestBody>) req -> req.setTransactionId("not-a-uuid"),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "validate request: transactionId: must be a valid UUID."),
                arguments("без rollbackTransactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setRollbackTransactionId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "rollbackTransactionId: cannot be blank"),
                arguments("пустой rollbackTransactionId",
                        (Consumer<RollbackRequestBody>) req -> req.setRollbackTransactionId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "rollbackTransactionId: cannot be blank"),
                arguments("невалидный rollbackTransactionId (не UUID)",
                        (Consumer<RollbackRequestBody>) req -> req.setRollbackTransactionId("not-a-uuid"),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "parse betTransaction: invalid UUID length: 10"),
                arguments("roundId превышает 255 символов",
                        (Consumer<RollbackRequestBody>) req -> req.setRoundId("a".repeat(256)),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "roundId: the length must be no more than 255"),
                arguments("без roundId",
                        (Consumer<RollbackRequestBody>) req -> req.setRoundId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "roundId: cannot be blank"),
                arguments("пустой roundId",
                        (Consumer<RollbackRequestBody>) req -> req.setRoundId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "roundId: cannot be blank")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeRollbackScenariosProvider")
    @DisplayName("Негативный сценарий получения роллбэка в игровой сессии:")
    void rollbackNegativeTest(
            String description,
            Consumer<RollbackRequestBody> requestModifier,
            HttpStatus expectedStatus,
            Integer expectedErrorCode,
            String expectedMessageSubstring)
    {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        var request = RollbackRequestBody.builder()
                .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                .amount(this.validRollbackAmount)
                .transactionId(UUID.randomUUID().toString())
                .rollbackTransactionId(this.betRequestBody.getTransactionId())
                .roundId(this.betRequestBody.getRoundId())
                .roundClosed(true)
                .playerId(this.registeredPlayer.getWalletData().getWalletUUID())
                .currency(this.registeredPlayer.getWalletData().getCurrency())
                .gameUuid(this.gameLaunchData.getDbGameSession().getGameUuid())
                .build();

        requestModifier.accept(request);

        step("Manager API: Попытка некорректного роллбэка", () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.rollback(
                            validCasinoId,
                            utils.createSignature(ApiEndpoints.ROLLBACK, request),
                            request
                    )
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll(
                    () -> assertEquals(expectedStatus.value(), thrownException.status(), "manager_api.rollback.status_code"),
                    () -> assertEquals(expectedErrorCode, error.getCode(), "manager_api.rollback.error_code"),
                    () -> assertTrue(error.getMessage().toLowerCase().contains(expectedMessageSubstring.toLowerCase()), "manager_api.rollback.error_message")
            );
        });
    }
}