package com.uplatform.wallet_tests.tests.wallet.gambling.bet;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий функциональность совершения ставок игроком
 * с заблокированным беттингом в системе Wallet.
 *
 * <p>Данный параметризованный тест проверяет сценарий, когда игрок, у которого заблокирован
 * беттинг (bettingEnabled=false), но разрешен гемблинг (gamblingEnabled=true),
 * пытается совершить ставку в казино различных типов (BET, TIPS, FREESPIN).
 * В данном сценарии ставки должны успешно проходить, так как блокировка беттинга
 * относится только к спортивным ставкам, а не к ставкам в казино.</p>
 *
 * <p>Тест использует единую игровую сессию и регистрационные данные игрока для всех тестовых
 * сценариев, а также предварительно устанавливает блокировку беттинга через CAP API.</p>
 *
 * <p><b>Проверяемые типы ставок:</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка.</li>
 *   <li>{@code TIPS} - чаевые.</li>
 *   <li>{@code FREESPIN} - бесплатные вращения.</li>
 * </ul>
 *
 * <p><b>Ожидаемый результат:</b> Система должна успешно обрабатывать все виды ставок в казино,
 * несмотря на блокировку беттинга у игрока.</p>
 */
@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Позитивные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet")
@TmsLink("NW-17")
class BetWhenBettingBlockedParametrizedTest {
    @Autowired private CapAdminClient capAdminClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private TestUtils utils;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal initialAdjustmentAmount = new BigDecimal("100.00");
    private final BigDecimal betAmount = new BigDecimal("10.00");

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

        step("CAP API: Блокировка беттинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(true)
                    .bettingEnabled(false)
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
                arguments(NatsGamblingTransactionOperation.BET),
                arguments(NatsGamblingTransactionOperation.TIPS),
                arguments(NatsGamblingTransactionOperation.FREESPIN)
        );
    }

    /**
     * @param type Тип операции ставки для проверки
     */
    @ParameterizedTest(name = "тип = {0}")
    @MethodSource("blockedBetProvider")
    @DisplayName("Совершение ставок игроком с заблокированным беттингом:")
    void test(
            NatsGamblingTransactionOperation type
    ) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Manager API: Совершение ставки", () -> {
            var request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(betAmount)
                    .transactionId(UUID.randomUUID().toString())
                    .type(type)
                    .roundId(UUID.randomUUID().toString())
                    .roundClosed(false)
                    .build();

            var response = managerClient.bet(
                    casinoId,
                    utils.createSignature(ApiEndpoints.BET, request),
                    request);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.bet.body_not_null"),
                    () -> assertEquals(request.getTransactionId(), response.getBody().getTransactionId(), "manager_api.bet.transactionId")
            );
        });
    }
}