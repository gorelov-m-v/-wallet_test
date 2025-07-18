package com.uplatform.wallet_tests.tests.wallet.gambling.signature;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import feign.FeignException;
import io.qameta.allure.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("GAS Signature")
@Suite("Негативные сценарии: GAS Signature")
@Tag("Gambling") @Tag("Wallet")
class QueriesSignatureNegativeParametrizedTest extends BaseParameterizedTest {
    @Autowired private ManagerClient managerClient;
    @Autowired private TestUtils utils;

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private String validSessionToken;
    private final String invalidCasinoId = "invalid-casino-" + UUID.randomUUID();
    private String invalidSignature;

    @RequiredArgsConstructor
    @Getter
    enum HeaderErrorType {
        MISSING_CASINO_ID(HttpStatus.BAD_REQUEST, 103, "missing api key"),
        INVALID_CASINO_ID(HttpStatus.NOT_FOUND, 103, "game contract not found"),
        MISSING_SIGNATURE(HttpStatus.UNAUTHORIZED, 102, "missing signature"),
        INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, 102, "invalid signature");

        private final HttpStatus expectedStatus;
        private final Integer expectedErrorCode;
        private final String expectedMessageSubstring;
    }

    @BeforeAll
    void setup() {
        step("Default Step: Регистрация нового пользователя", () -> {
            registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(registeredPlayer, "default_step.registration");
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
            assertNotNull(gameLaunchData, "default_step.create_game_session");
            validSessionToken = gameLaunchData.getDbGameSession().getGameSessionUuid();
        });

        step("Prerequisite Step: Генерация заведомо невалидной подписи", () -> {
            invalidSignature = utils.createSignature(
                    ApiEndpoints.BALANCE,
                    "some=garbage",
                    null
            );
            assertNotNull(invalidSignature, "prerequisite.invalid_signature");
        });
    }

    static Stream<Arguments> negativeHeaderProvider() {
        var pathDescription = "/balance";
        Stream.Builder<Arguments> builder = Stream.builder();
        for (HeaderErrorType errorType : HeaderErrorType.values()) {
            builder.add(arguments(
                    pathDescription + " - " + errorType.name(),
                    errorType
            ));
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeHeaderProvider")
    @DisplayName("Проверка ошибок валидации заголовков гэмблинг коллбэков Queries API:")
    void balanceHeaderNegativeTest(String description, HeaderErrorType headerErrorType) {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        var validQueryString = "sessionToken=" + this.validSessionToken;
        var validSignature = utils.createSignature(
                ApiEndpoints.BALANCE,
                validQueryString,
                null
        );

        String casinoIdToSend;
        String signatureToSend;

        switch (headerErrorType) {
            case MISSING_CASINO_ID -> {
                casinoIdToSend = null;
                signatureToSend = validSignature;
            }
            case INVALID_CASINO_ID -> {
                casinoIdToSend = invalidCasinoId;
                signatureToSend = validSignature;
            }
            case MISSING_SIGNATURE -> {
                casinoIdToSend = validCasinoId;
                signatureToSend = null;
            }
            case INVALID_SIGNATURE -> {
                casinoIdToSend = validCasinoId;
                signatureToSend = this.invalidSignature;
            }
            default -> throw new IllegalArgumentException("Неизвестный тип ошибки заголовка: " + headerErrorType);
        }

        step("Вызов Manager API: " + description, () -> {
            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.getBalance(
                            casinoIdToSend,
                            signatureToSend,
                            validSessionToken
                    ),
                    "manager_api.error.expected_exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll(
                    () -> assertEquals(headerErrorType.getExpectedStatus().value(), thrownException.status(), "manager_api.error.status_code"),
                    () -> assertEquals(headerErrorType.getExpectedErrorCode(), error.getCode(), "manager_api.error.code"),
                    () -> assertTrue(error.getMessage().toLowerCase().contains(headerErrorType.getExpectedMessageSubstring().toLowerCase()), "manager_api.error.message_contains")
            );
        });
    }
}