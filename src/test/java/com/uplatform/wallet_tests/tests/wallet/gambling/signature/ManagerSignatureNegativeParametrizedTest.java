package com.uplatform.wallet_tests.tests.wallet.gambling.signature;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.*;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("Gambling")
@Feature("GAS Signature")
@Suite("Негативные сценарии: GAS Signature")
@Tag("Gambling") @Tag("Wallet")
class ManagerSignatureNegativeParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private GameLaunchData gameLaunchData;
    private final BigDecimal defaultAmount = new BigDecimal("10.00");
    private final String invalidCasinoId = "invalid-casino-" + UUID.randomUUID();
    private String invalidSignature;

    @FunctionalInterface
    interface ManagerClientExecutor {
        void execute(ManagerClient client, String casinoId, String signature, Object requestBody);
    }

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
            registeredPlayer = defaultTestSteps.registerNewPlayer(new BigDecimal("2000.00"));
        });

        step("Default Step: Создание игровой сессии", () -> {
            gameLaunchData = defaultTestSteps.createGameSession(registeredPlayer);
        });

        step("Prerequisite Step: Генерация заведомо невалидной подписи (неверный путь)", () -> {
            invalidSignature = utils.createSignature(
                    ApiEndpoints.UNKNOWN,
                    createValidBetRequestBody()
            );
        });
    }

    private BetRequestBody createValidBetRequestBody() {
        return BetRequestBody.builder()
                .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                .amount(defaultAmount)
                .transactionId(UUID.randomUUID().toString())
                .type(NatsGamblingTransactionOperation.BET)
                .roundId(UUID.randomUUID().toString())
                .roundClosed(false)
                .build();
    }

    private WinRequestBody createValidWinRequestBody() {
        return WinRequestBody.builder()
                .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                .amount(defaultAmount)
                .transactionId(UUID.randomUUID().toString())
                .type(NatsGamblingTransactionOperation.WIN)
                .roundId(UUID.randomUUID().toString())
                .roundClosed(true)
                .build();
    }

    private RefundRequestBody createValidRefundRequestBody() {
        return RefundRequestBody.builder()
                .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                .amount(new BigDecimal("10.10"))
                .transactionId(UUID.randomUUID().toString())
                .betTransactionId(UUID.randomUUID().toString())
                .roundId(UUID.randomUUID().toString())
                .roundClosed(true)
                .playerId(registeredPlayer.getWalletData().getPlayerUUID())
                .currency(registeredPlayer.getWalletData().getCurrency())
                .gameUuid(gameLaunchData.getDbGameSession().getGameUuid())
                .build();
    }

    private RollbackRequestBody createValidRollbackRequestBody() {
        return RollbackRequestBody.builder()
                .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                .amount(new BigDecimal("10.10"))
                .transactionId(UUID.randomUUID().toString())
                .rollbackTransactionId(UUID.randomUUID().toString())
                .roundId(UUID.randomUUID().toString())
                .roundClosed(true)
                .playerId(registeredPlayer.getWalletData().getPlayerUUID())
                .currency(registeredPlayer.getWalletData().getCurrency())
                .gameUuid(gameLaunchData.getDbGameSession().getGameUuid())
                .build();
    }

    private TournamentRequestBody createValidTournamentRequestBody() {
        return TournamentRequestBody.builder()
                .amount(defaultAmount)
                .playerId(registeredPlayer.getWalletData().getPlayerUUID())
                .sessionToken(gameLaunchData.getDbGameSession().getGameSessionUuid())
                .transactionId(UUID.randomUUID().toString())
                .gameUuid(gameLaunchData.getDbGameSession().getGameUuid())
                .roundId(UUID.randomUUID().toString())
                .providerUuid(gameLaunchData.getDbGameSession().getProviderUuid())
                .build();
    }

    static Stream<Arguments> negativeHeaderProvider() {
        ApiEndpoints[] paths = {ApiEndpoints.BET, ApiEndpoints.WIN, ApiEndpoints.REFUND, ApiEndpoints.ROLLBACK, ApiEndpoints.TOURNAMENT};
        Stream.Builder<Arguments> builder = Stream.builder();
        for (ApiEndpoints path : paths) {
            for (HeaderErrorType errorType : HeaderErrorType.values()) {
                builder.add(arguments(
                        path + " - " + errorType.name(),
                        path,
                        errorType
                ));
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeHeaderProvider")
    @DisplayName("Проверка ошибок валидации заголовков гэмблинг коллбэков Manager API:")
    void headerNegativeTest(
            String description,
            ApiEndpoints signaturePathSuffix,
            HeaderErrorType headerErrorType)
    {
        final String validCasinoId = configProvider.getEnvironmentConfig().getApi().getManager().getCasinoId();

        Supplier<?> validBodySupplier;
        ManagerClientExecutor clientExecutor;

        switch (signaturePathSuffix) {
            case BET -> {
                validBodySupplier = this::createValidBetRequestBody;
                clientExecutor = (client, casinoId, signature, body) -> client.bet(casinoId, signature, (BetRequestBody) body);
            }
            case WIN -> {
                validBodySupplier = this::createValidWinRequestBody;
                clientExecutor = (client, casinoId, signature, body) -> client.win(casinoId, signature, (WinRequestBody) body);
            }
            case REFUND -> {
                validBodySupplier = this::createValidRefundRequestBody;
                clientExecutor = (client, casinoId, signature, body) -> client.refund(casinoId, signature, (RefundRequestBody) body);
            }
            case ROLLBACK -> {
                validBodySupplier = this::createValidRollbackRequestBody;
                clientExecutor = (client, casinoId, signature, body) -> client.rollback(casinoId, signature, (RollbackRequestBody) body);
            }
            case TOURNAMENT -> {
                validBodySupplier = this::createValidTournamentRequestBody;
                clientExecutor = (client, casinoId, signature, body) -> client.tournament(casinoId, signature, (TournamentRequestBody) body);
            }
            default -> {
                fail("Unsupported path provided by MethodSource: " + signaturePathSuffix);
                return;
            }
        }

        var validSignature = utils.createSignature(
                signaturePathSuffix,
                validBodySupplier.get()
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
                    () -> clientExecutor.execute(managerClient, casinoIdToSend, signatureToSend, validBodySupplier.get()),
                    "manager_api.error.expected_exception"
            );

            var error = utils.parseFeignExceptionContent(thrownException, GamblingError.class);

            assertAll(
                    () -> assertEquals(headerErrorType.getExpectedErrorCode(), error.getCode(), "manager_api.error.code"),
                    () -> assertNotNull(error.getMessage(), "manager_api.error.message_not_null"),
                    () -> assertTrue(error.getMessage().toLowerCase().contains(headerErrorType.getExpectedMessageSubstring().toLowerCase()), "manager_api.error.message_contains")
            );
        });
    }
}