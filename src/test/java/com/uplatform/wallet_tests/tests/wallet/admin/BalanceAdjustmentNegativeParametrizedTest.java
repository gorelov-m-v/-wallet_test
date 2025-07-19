package com.uplatform.wallet_tests.tests.wallet.admin;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.http.cap.dto.errors.ValidationErrorResponse;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("BalanceAdjustment")
@Suite("Негативные сценарии: BalanceAdjustment")
@Tag("Wallet") @Tag("CAP")
class BalanceAdjustmentNegativeParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private final BigDecimal validAdjustmentAmount = new BigDecimal("50.00");
    private String platformNodeId;

    private static class TestCase {
        final String description;
        final Consumer<CreateBalanceAdjustmentRequest> requestModifier;
        final String playerUuid;
        final String authHeader;
        final String nodeId;
        final Integer expectedStatus;
        final String expectedMessageSubstring;

        TestCase(String description,
                 Consumer<CreateBalanceAdjustmentRequest> requestModifier,
                 String playerUuid,
                 String authHeader,
                 String nodeId,
                 Integer expectedStatus,
                 String expectedMessageSubstring) {
            this.description = description;
            this.requestModifier = requestModifier;
            this.playerUuid = playerUuid;
            this.authHeader = authHeader;
            this.nodeId = nodeId;
            this.expectedStatus = expectedStatus;
            this.expectedMessageSubstring = expectedMessageSubstring;
        }
    }

    @BeforeAll
    void setup() {
        this.platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        step("Default Step: Регистрация нового пользователя", () -> {
            this.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(this.registeredPlayer, "default_step.registration");
        });
    }

    static Stream<Arguments> negativeBalanceAdjustmentScenariosProvider() {
        TestCase[] testCases = new TestCase[] {
                new TestCase(
                        "Параметр пути playerUuid: рандомный UUID",
                        req -> {},
                        UUID.randomUUID().toString(),
                        null,
                        null,
                        404,
                        "Player not found"
                ),
                new TestCase(
                        "Параметр пути playerUuid: невалидный формат UUID",
                        req -> {},
                        "not-a-uuid",
                        null,
                        null,
                        404,
                        "Not found"
                ),
                new TestCase(
                        "Заголовок Authorization: отсутствие заголовка",
                        req -> {},
                        null,
                        "",
                        null,
                        401,
                        "Full authentication is required to access this resource."
                ),
                new TestCase(
                        "Заголовок Authorization: пустая строка",
                        req -> {},
                        null,
                        "",
                        null,
                        401,
                        "Full authentication is required to access this resource."
                ),
                new TestCase(
                        "Параметр тела amount: отсутствует",
                        req -> req.setAmount(null),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела amount: отрицательный",
                        req -> req.setAmount(new BigDecimal("-50.00")),
                        null,
                        null,
                        null,
                        400,
                        "amount must be positive"
                ),
                new TestCase(
                        "Параметр тела amount: превышает баланс",
                        req -> {
                            req.setAmount(new BigDecimal("5000.00"));
                            req.setDirection(DirectionType.DECREASE);
                        },
                        null,
                        null,
                        null,
                        400,
                        "balance is not enough"
                ),
                new TestCase(
                        "Параметр тела currency: отсутствует",
                        req -> req.setCurrency(null),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела currency: пустая строка",
                        req -> req.setCurrency(""),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела currency: не совпадает с валютой игрока",
                        req -> req.setCurrency("BTC"),
                        null,
                        null,
                        null,
                        404,
                        "Player not found"
                ),
                new TestCase(
                        "Параметр тела reason: отсутствует",
                        req -> req.setReason(null),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела reason: пустая строка",
                        req -> req.setReason(ReasonType.EMPTY),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела reason: неизвестный",
                        req -> req.setReason(ReasonType.UNKNOWN),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела operationType: отсутствует",
                        req -> req.setOperationType(null),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела operationType: пустая строка",
                        req -> req.setOperationType(OperationType.EMPTY),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела operationType: неизвестный",
                        req -> req.setOperationType(OperationType.UNKNOWN),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела direction: отсутствует",
                        req -> req.setDirection(null),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела direction: пустая строка",
                        req -> req.setDirection(DirectionType.EMPTY),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                ),
                new TestCase(
                        "Параметр тела direction: неизвестный",
                        req -> req.setDirection(DirectionType.UNKNOWN),
                        null,
                        null,
                        null,
                        400,
                        "Validation error"
                )
        };

        return Stream.of(testCases)
                .map(tc -> arguments(
                        tc.description,
                        tc.requestModifier,
                        tc.playerUuid,
                        tc.authHeader,
                        tc.nodeId,
                        tc.expectedStatus,
                        tc.expectedMessageSubstring
                ));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeBalanceAdjustmentScenariosProvider")
    @DisplayName("Создание корректировки баланса: негативные сценарии")
    void createBalanceAdjustmentNegativeTest(
            String description,
            Consumer<CreateBalanceAdjustmentRequest> requestModifier,
            String customPlayerUuid,
            String customAuthHeader,
            String customNodeId,
            Integer expectedStatus,
            String expectedMessage)
    {
        final class TestContext {
            CreateBalanceAdjustmentRequest request;
            String playerUuid;
            String authHeader;
            String nodeId;
        }

        final TestContext ctx = new TestContext();

        step("Подготовка параметров запроса", () -> {
            ctx.request = CreateBalanceAdjustmentRequest.builder()
                    .currency(registeredPlayer.getWalletData().getCurrency())
                    .amount(validAdjustmentAmount)
                    .reason(ReasonType.MALFUNCTION)
                    .operationType(OperationType.CORRECTION)
                    .direction(DirectionType.INCREASE)
                    .build();

            requestModifier.accept(ctx.request);

            ctx.playerUuid = (customPlayerUuid != null)
                    ? customPlayerUuid
                    : registeredPlayer.getWalletData().getPlayerUUID();

            ctx.authHeader = (customAuthHeader != null)
                    ? customAuthHeader
                    : utils.getAuthorizationHeader();

            ctx.nodeId = (customNodeId != null)
                    ? customNodeId
                    : platformNodeId;
        });

        step("CAP API: Попытка создания корректировки баланса с некорректными параметрами - " + description, () -> {
            var exception = assertThrows(
                    FeignException.class,
                    () -> capAdminClient.createBalanceAdjustment(
                            ctx.playerUuid,
                            ctx.authHeader,
                            ctx.nodeId,
                            "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                            ctx.request
                    ),
                    "cap_api.create_balance_adjustment.expected_exception"
            );

            var error = utils.parseFeignExceptionContent(exception, ValidationErrorResponse.class);

            assertAll("cap_api.error.validation_structure",
                    () -> assertEquals(expectedStatus, error.getCode(), "cap_api.error.code"),
                    () -> assertEquals(expectedMessage, error.getMessage(), "cap_api.error.message")
            );
        });
    }
}