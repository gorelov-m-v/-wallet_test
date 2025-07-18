package com.uplatform.wallet_tests.tests.wallet.gambling.balance;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingError;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("/balance")
@Suite("Негативные сценарии получения баланса игрока в игровой сессии")
@Tag("Gambling") @Tag("Wallet")
class GetBalanceNegativeParametrizedTest extends BaseParameterizedTest {

    static Stream<Arguments> negativeBalanceScenariosProvider() {
        final int VALIDATION_ERROR_CODE = 103;
        final int BUSINESS_LOGIC_ERROR_CODE = 101;
        final int MISSING_PARAM_CODE = 100;

        return Stream.of(
                arguments("пустой sessionToken",
                        null,
                        HttpStatus.BAD_REQUEST,
                        MISSING_PARAM_CODE,
                        "missing session token"),

                arguments("несуществующий sessionToken",
                        UUID.randomUUID().toString(),
                        HttpStatus.NOT_FOUND,
                        BUSINESS_LOGIC_ERROR_CODE,
                        "failed to find session: not found"),

                arguments("невалидный формат sessionToken",
                        "not-a-valid-uuid",
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_CODE,
                        "failed to parse SessionToken: invalid UUID length: 16")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeBalanceScenariosProvider")
    @DisplayName("Негативный сценарий получения баланса игрока в игровой сессии:")
    void balanceNegativeTest(
            String description,
            String sessionTokenToTest,
            HttpStatus expectedStatus,
            Integer expectedErrorCode,
            String expectedMessageSubstring)
    {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        step("Manager API: Попытка получения баланса с невалидными данными - " + description, () -> {
            var queryString = (sessionTokenToTest == null) ? null : "sessionToken=" + sessionTokenToTest;

            var signature = utils.createSignature(
                    ApiEndpoints.BALANCE,
                    queryString,
                    null
            );

            var thrownException = assertThrows(
                    FeignException.class,
                    () -> managerClient.getBalance(
                            validCasinoId,
                            signature,
                            sessionTokenToTest
                    )
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll(
                    () -> assertEquals(expectedStatus.value(), thrownException.status(), "manager_api.balance.status_code"),
                    () -> assertEquals(expectedErrorCode, error.getCode(), "manager_api.balance.error_code"),
                    () -> assertTrue(error.getMessage().toLowerCase().contains(expectedMessageSubstring.toLowerCase()), "manager_api.balance.error_message")
            );
        });
    }
}