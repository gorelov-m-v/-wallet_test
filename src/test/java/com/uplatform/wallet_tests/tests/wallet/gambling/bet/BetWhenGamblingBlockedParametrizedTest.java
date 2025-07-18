package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
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
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий функциональность совершения ставок игроком
 * с заблокированным гемблингом в системе Wallet.
 *
 * <p>Данный параметризованный тест проверяет сценарий, когда игрок, у которого заблокирован
 * гемблинг (gamblingEnabled=false), пытается совершить ставку в казино различных типов
 * (BET, TIPS, FREESPIN). В данном сценарии ставки должны быть отклонены с соответствующей
 * ошибкой, указывающей на блокировку гемблинга для игрока.</p>
 *
 * <p>Тест использует единую игровую сессию и регистрационные данные игрока для всех тестовых
 * сценариев, а также предварительно устанавливает блокировку гемблинга через CAP API.</p>
 *
 * <p><b>Проверяемые типы ставок:</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка.</li>
 *   <li>{@code TIPS} - чаевые.</li>
 *   <li>{@code FREESPIN} - бесплатные вращения.</li>
 * </ul>
 *
 * <p><b>Ожидаемый результат:</b> API возвращает ошибку с кодом {@link GamblingErrors#PLAYER_BLOCKED}
 * и соответствующим сообщением о блокировке игрока.</p>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
class BetWhenGamblingBlockedParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal initialAdjustmentAmount = new BigDecimal("100.00");
    private final BigDecimal betAmount = generateBigDecimalAmount(initialAdjustmentAmount);

    @BeforeAll
    void setup() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();

        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });

        step("CAP API: Блокировка гемблинга (gamblingEnabled=false)", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(false)
                    .bettingEnabled(true)
                    .build();

            var response = capAdminClient.updateBlockers(
                    registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });
    }

    static Stream<Arguments> blockedBetProvider() {
        return Stream.of(
                arguments(
                        NatsGamblingTransactionOperation.BET,
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.PLAYER_BLOCKED,
                        "player was blocked"
                ),
                arguments(
                        NatsGamblingTransactionOperation.TIPS,
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.PLAYER_BLOCKED,
                        "player was blocked"
                ),
                arguments(
                        NatsGamblingTransactionOperation.FREESPIN,
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.PLAYER_BLOCKED,
                        "player was blocked"
                )
        );
    }

    /**
     * @param type Тип операции ставки для проверки
     * @param expectedStatus Ожидаемый HTTP статус ответа
     * @param expectedErrorCode Ожидаемый код ошибки
     * @param expectedMessage Ожидаемое сообщение об ошибке
     */
    @ParameterizedTest(name = "тип = {0}")
    @MethodSource("blockedBetProvider")
    @DisplayName("Совершение ставки игроком с заблокированным гемблингом:")
    void test(
            NatsGamblingTransactionOperation type,
            HttpStatus expectedStatus,
            GamblingErrors expectedErrorCode,
            String expectedMessage
    ) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Manager API: Попытка совершения ставки с заблокированным гемблингом", () -> {
            var request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(type)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.bet(
                            casinoId,
                            utils.createSignature(ApiEndpoints.BET, request),
                            request
                    ),
                    "manager_api.bet.exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll("Проверка деталей ошибки",
                    () -> assertEquals(expectedStatus.value(), thrownException.status(), "manager_api.error.status_code"),
                    () -> assertEquals(expectedErrorCode.getCode(), error.getCode(), "manager_api.error.code"),
                    () -> assertEquals(expectedMessage, error.getMessage(), "manager_api.error.message")
            );
        });
    }
}