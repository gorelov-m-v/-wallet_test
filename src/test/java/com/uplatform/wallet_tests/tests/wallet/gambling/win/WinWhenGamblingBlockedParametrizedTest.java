package com.uplatform.wallet_tests.tests.wallet.gambling.win;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
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
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий функциональность получения выигрышей игроком
 * с заблокированным гемблингом в системе Wallet.
 *
 * <p>Данный параметризованный тест проверяет сценарий, когда игрок, у которого заблокирован
 * гемблинг (gamblingEnabled=false), но разрешен беттинг (bettingEnabled=true),
 * пытается получить выигрыш в казино различных типов (WIN, FREESPIN, JACKPOT).
 * В данном сценарии получение выигрышей должно тем не менее успешно проходить, несмотря на
 * блокировку гемблинга, так как система разрешает получать выигрыши даже при запрете
 * на совершение новых ставок.</p>
 *
 * <p>Тест использует единую игровую сессию и регистрационные данные игрока для всех тестовых
 * сценариев, а также предварительно устанавливает блокировку гемблинга через CAP API.</p>
 *
 * <p><b>Проверяемые типы выигрышей:</b></p>
 * <ul>
 *   <li>{@code WIN} - обычный выигрыш.</li>
 *   <li>{@code FREESPIN} - выигрыш от бесплатных вращений.</li>
 *   <li>{@code JACKPOT} - выигрыш джекпота.</li>
 * </ul>
 *
 * <p><b>Ожидаемый результат:</b> Система должна успешно обрабатывать все виды выигрышей в казино,
 * несмотря на блокировку гемблинга у игрока. Это продиктовано логикой предоставления игроку
 * возможности получить выигрыш по ранее сделанным ставкам.</p>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/win")
@Suite("Позитивные сценарии: /win")
@Tag("Gambling") @Tag("Wallet")
class WinWhenGamblingBlockedParametrizedTest extends BaseParameterizedTest {
    @Autowired private CapAdminClient capAdminClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal initialAdjustmentAmount = new BigDecimal("100.00");
    private final BigDecimal winAmount = generateBigDecimalAmount(initialAdjustmentAmount);

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

    static Stream<Arguments> blockedWinProvider() {
        return Stream.of(
                arguments(NatsGamblingTransactionOperation.WIN),
                arguments(NatsGamblingTransactionOperation.FREESPIN),
                arguments(NatsGamblingTransactionOperation.JACKPOT)
        );
    }

    /**
     * @param type Тип операции выигрыша для проверки (WIN, FREESPIN, JACKPOT)
     */
    @ParameterizedTest(name = "тип = {0}")
    @MethodSource("blockedWinProvider")
    @DisplayName("Получение выигрыша игроком с заблокированным гемблингом:")
    void test(NatsGamblingTransactionOperation type) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Manager API: Получение выигрыша типа " + type, () -> {
            var transactionId = UUID.randomUUID().toString();
            var roundId = UUID.randomUUID().toString();

            var request = WinRequestBody.builder()
                    .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(winAmount)
                    .transactionId(transactionId)
                    .type(type)
                    .roundId(roundId)
                    .roundClosed(true)
                    .build();

            var response = managerClient.win(
                    casinoId,
                    utils.createSignature(ApiEndpoints.WIN, request),
                    request);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.win.body_not_null"),
                    () -> assertEquals(transactionId, response.getBody().getTransactionId(), "manager_api.win.transactionId"),
                    () -> assertTrue(response.getBody().getBalance().compareTo(BigDecimal.ZERO) > 0, "manager_api.win.balance")
            );
        });
    }
}