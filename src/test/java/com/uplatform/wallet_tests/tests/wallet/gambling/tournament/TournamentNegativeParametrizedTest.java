package com.uplatform.wallet_tests.tests.wallet.gambling.tournament;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
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

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/tournament")
@Suite("Негативные сценарии: /tournament")
@Tag("Gambling") @Tag("Wallet")
class TournamentNegativeParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal adjustmentAmount = new BigDecimal("20.00");
    private final BigDecimal validTournamentAmount = new BigDecimal("10.00");

    @BeforeAll
    void setup() {
        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer(this.adjustmentAmount);
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            this.gameLaunchData = defaultTestSteps.createGameSession(this.registeredPlayer);
            assertNotNull(this.gameLaunchData, "default_step.create_game_session");
        });
    }

    static Stream<Arguments> negativeTournamentScenariosProvider() {
        final int VALIDATION_ERROR_CODE = 103;
        final int MISSING_TOKEN_CODE = 100;

        return Stream.of(
                arguments("без sessionToken",
                        (Consumer<TournamentRequestBody>) req -> req.setSessionToken(null),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN_CODE,
                        "missing session token"),
                arguments("пустой sessionToken",
                        (Consumer<TournamentRequestBody>) req -> req.setSessionToken(""),
                        HttpStatus.BAD_REQUEST,
                        MISSING_TOKEN_CODE,
                        "missing session token"),
                arguments("отрицательный amount",
                        (Consumer<TournamentRequestBody>) req -> req.setAmount(new BigDecimal("-1.0")),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "amount: value [-1] must be greater or equal than [0]."),
                arguments("без transactionId",
                        (Consumer<TournamentRequestBody>) req -> req.setTransactionId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "transactionId: cannot be blank"),
                arguments("пустой transactionId",
                        (Consumer<TournamentRequestBody>) req -> req.setTransactionId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "transactionId: cannot be blank"),
                arguments("невалидный transactionId (не UUID)",
                        (Consumer<TournamentRequestBody>) req -> req.setTransactionId("not-a-uuid"),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "transactionId: must be a valid UUID"),
                arguments("roundId превышает 255 символов",
                        (Consumer<TournamentRequestBody>) req -> req.setRoundId("a".repeat(256)),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "roundId: the length must be no more than 255"),
                arguments("без roundId",
                        (Consumer<TournamentRequestBody>) req -> req.setRoundId(null),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "roundId: cannot be blank"),
                arguments("пустой roundId",
                        (Consumer<TournamentRequestBody>) req -> req.setRoundId(""),
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "roundId: cannot be blank")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeTournamentScenariosProvider")
    @DisplayName("Негативный сценарий получения выигрыша в турнире:")
    void tournamentNegativeTest(
            String description,
            Consumer<TournamentRequestBody> requestModifier,
            HttpStatus expectedStatus,
            Integer expectedErrorCode,
            String expectedMessageSubstring)
    {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        var requestBody = TournamentRequestBody.builder()
                .amount(this.validTournamentAmount)
                .playerId(this.registeredPlayer.getWalletData().getPlayerUUID())
                .sessionToken(this.gameLaunchData.getDbGameSession().getGameSessionUuid())
                .transactionId(UUID.randomUUID().toString())
                .gameUuid(this.gameLaunchData.getDbGameSession().getGameUuid())
                .roundId(UUID.randomUUID().toString())
                .providerUuid(this.gameLaunchData.getDbGameSession().getProviderUuid())
                .build();

        requestModifier.accept(requestBody);

        step("Manager API: Попытка некорректного начисления турнирного выигрыша - " + description, () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.tournament(
                            validCasinoId,
                            utils.createSignature(ApiEndpoints.TOURNAMENT, requestBody),
                            requestBody
                    )
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll(
                    () -> assertEquals(expectedStatus.value(), thrownException.status(), "manager_api.tournament.status_code"),
                    () -> assertEquals(expectedErrorCode, error.getCode(), "manager_api.tournament.error_code"),
                    () -> assertTrue(error.getMessage().toLowerCase().contains(expectedMessageSubstring.toLowerCase()), "manager_api.tournament.error_message")
            );
        });
    }
}