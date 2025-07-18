package com.uplatform.wallet_tests.tests.wallet.gambling.bet;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.casino_loss.SetCasinoLossLimitRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.GamblingErrors;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsLimitChangedV2Payload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
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
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Интеграционный тест, проверяющий функциональность системы ограничений
 * на проигрыш в казино (CasinoLossLimit) при совершении ставок.
 *
 * <p>Данный параметризованный тест проверяет корректность обработки запросов
 * на совершение ставок, превышающих установленный лимит на проигрыш в казино.
 * Тест проверяет все типы ставок (BET, TIPS, FREESPIN) и подтверждает,
 * что система корректно отклоняет их, когда сумма ставки превышает
 * установленный лимит на проигрыш.</p>
 *
 * <p>Тест предварительно настраивает лимит на проигрыш для игрока через Public API
 * и затем пытается сделать ставку, превышающую этот лимит.</p>
 *
 * <p><b>Проверяемые типы ставок:</b></p>
 * <ul>
 *   <li>{@code BET} - обычная ставка.</li>
 *   <li>{@code TIPS} - чаевые.</li>
 *   <li>{@code FREESPIN} - бесплатные вращения.</li>
 * </ul>
 *
 * <p><b>Ожидаемый результат:</b> API возвращает ошибку с кодом {@link GamblingErrors#LIMIT_IS_OVER}
 * и соответствующим сообщением о превышении лимита.</p>
 */
@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/bet")
@Suite("Негативные сценарии: /bet")
@Tag("Gambling") @Tag("Wallet") @Tag("Limits")
class BetWhenCasinoLossLimitParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal limitAmount =  new BigDecimal("150.00");
    private final BigDecimal initialAdjustmentAmount = new BigDecimal("2000.00");

    @BeforeAll
    void setup() {
        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer(initialAdjustmentAmount);
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
        });

        step("Public API: Установка лимита на проигрыш", () -> {
            var request = SetCasinoLossLimitRequest.builder()
                    .currency(registeredPlayer.getWalletData().getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .amount(limitAmount.toString())
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .build();

            var response = publicClient.setCasinoLossLimit(
                    registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "public_api.status_code");
        });

        step("NATS: получение события limit_changed_v2", () -> {
            var subject = natsClient.buildWalletSubject(
                    registeredPlayer.getWalletData().getPlayerUUID(),
                    registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsLimitChangedV2Payload, String> filter = (payload, typeHeader) ->
                    NatsEventType.LIMIT_CHANGED_V2.getHeaderValue().equals(typeHeader);

            var limitCreateEvent = natsClient.findMessageAsync(subject, NatsLimitChangedV2Payload.class, filter).get();

            assertNotNull(limitCreateEvent, "nats.event.limit_changed_v2");
        });
    }

    static Stream<Arguments> blockedBetProvider() {
        return Stream.of(
                arguments(
                        NatsGamblingTransactionOperation.BET,
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.LIMIT_IS_OVER,
                        "limit is over"
                ),
                arguments(
                        NatsGamblingTransactionOperation.TIPS,
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.LIMIT_IS_OVER,
                        "limit is over"
                ),
                arguments(
                        NatsGamblingTransactionOperation.FREESPIN,
                        HttpStatus.BAD_REQUEST,
                        GamblingErrors.LIMIT_IS_OVER,
                        "limit is over"
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
    @DisplayName("Совершение ставки в казино, превышающей CasinoLossLimit:")
    void test(
            NatsGamblingTransactionOperation type,
            HttpStatus expectedStatus,
            GamblingErrors expectedErrorCode,
            String expectedMessage
    ) {
        final String casinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Manager API: Попытка совершения ставки, превышающей лимит", () -> {
            var request = BetRequestBody.builder()
                    .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                    .amount(limitAmount.add(generateBigDecimalAmount(initialAdjustmentAmount)))
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