package com.uplatform.wallet_tests.tests.wallet.admin;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.errors.ValidationErrorResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import feign.FeignException;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("PlayerBlockers")
@Suite("Негативные сценарии: PlayerBlockers")
@Tag("Wallet") @Tag("CAP")
class BlockersNegativeParametrizedTest extends BaseParameterizedTest {

    private RegisteredPlayerData registeredPlayer;
    private String platformNodeId;

    private static class TestCase {
        final String description;
        final Consumer<UpdateBlockersRequest> requestModifier;
        final String playerUuid;
        final String authHeader;
        final String nodeId;
        final Integer expectedStatus;
        final String expectedMessageSubstring;
        final Map<String, List<String>> expectedFieldErrors;

        TestCase(String description,
                 Consumer<UpdateBlockersRequest> requestModifier,
                 String playerUuid,
                 String authHeader,
                 String nodeId,
                 Integer expectedStatus,
                 String expectedMessageSubstring,
                 Map<String, List<String>> expectedFieldErrors) {
            this.description = description;
            this.requestModifier = requestModifier;
            this.playerUuid = playerUuid;
            this.authHeader = authHeader;
            this.nodeId = nodeId;
            this.expectedStatus = expectedStatus;
            this.expectedMessageSubstring = expectedMessageSubstring;
            this.expectedFieldErrors = expectedFieldErrors;
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

    static Stream<Arguments> negativeBlockersScenariosProvider() {
        TestCase[] testCases = new TestCase[] {
                new TestCase(
                        "Параметр пути playerUuid: рандомный UUID",
                        req -> {},
                        UUID.randomUUID().toString(),
                        null,
                        null,
                        404,
                        "field:[wallet] msg:[not found]",
                        Map.of("wallet", List.of("Not found."))
                ),
                new TestCase(
                        "Параметр пути playerUuid: невалидный формат UUID",
                        req -> {},
                        "not-a-uuid",
                        null,
                        null,
                        400,
                        "Validation message",
                        Map.of("playerId", List.of("Must be a valid UUID."))
                ),
                new TestCase(
                        "Заголовок Authorization: отсутствие заголовка",
                        req -> {},
                        null,
                        "",
                        null,
                        401,
                        "Full authentication is required to access this resource.",
                        null
                ),
                new TestCase(
                        "Заголовок Authorization: пустая строка",
                        req -> {},
                        null,
                        "",
                        null,
                        401,
                        "Full authentication is required to access this resource.",
                        null
                ),
                new TestCase(
                        "Параметр тела gamblingEnabled: отсутствует",
                        req -> req.setGamblingEnabled(null),
                        null,
                        null,
                        null,
                        400,
                        "Validation error",
                        Map.of("gamblingEnabled", List.of("value.not.null"))
                ),
                new TestCase(
                        "Параметр тела bettingEnabled: отсутствует",
                        req -> req.setBettingEnabled(null),
                        null,
                        null,
                        null,
                        400,
                        "Validation error",
                        Map.of("bettingEnabled", List.of("value.not.null"))
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
                        tc.expectedMessageSubstring,
                        tc.expectedFieldErrors
                ));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeBlockersScenariosProvider")
    @DisplayName("Обновление блокировок игрока: негативные сценарии")
    void updateBlockersNegativeTest(
            String description,
            Consumer<UpdateBlockersRequest> requestModifier,
            String customPlayerUuid,
            String customAuthHeader,
            String customNodeId,
            Integer expectedStatus,
            String expectedMessage,
            Map<String, List<String>> expectedFieldErrors)
    {
        final class TestContext {
            UpdateBlockersRequest request;
            String playerUuid;
            String authHeader;
            String nodeId;
        }

        final TestContext ctx = new TestContext();

        step("Подготовка параметров запроса", () -> {
            ctx.request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(true)
                    .bettingEnabled(true)
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

        step("CAP API: Попытка обновления блокировок с некорректными параметрами - " + description, () -> {
            var exception = assertThrows(
                    FeignException.class,
                    () -> capAdminClient.updateBlockers(
                            ctx.playerUuid,
                            ctx.authHeader,
                            ctx.nodeId,
                            ctx.request
                    ),
                    "cap_api.update_blockers.expected_exception"
            );

            var error = utils.parseFeignExceptionContent(exception, ValidationErrorResponse.class);

            assertAll("cap_api.error.validation_structure",
                    () -> assertEquals(expectedStatus, error.getCode(), "cap_api.error.code"),
                    () -> assertEquals(expectedMessage, error.getMessage(), "cap_api.error.message"),
                    () -> {
                        if (expectedFieldErrors != null && !expectedFieldErrors.isEmpty()) {
                            expectedFieldErrors.forEach((expectedField, expectedErrorMessagesList) -> {
                                assertTrue(error.getErrors().containsKey(expectedField), "cap_api.error.errors.key");
                                var actualErrorMessagesList = error.getErrors().get(expectedField);
                                if (expectedErrorMessagesList != null && !expectedErrorMessagesList.isEmpty()) {
                                    var expectedFirstMessage = expectedErrorMessagesList.get(0);
                                    assertTrue(actualErrorMessagesList.contains(expectedFirstMessage), "cap_api.error.errors.value");
                                }
                            });
                        } else if (expectedFieldErrors == null) {
                            if (error.getErrors() != null) {
                                assertTrue(error.getErrors().isEmpty(), "cap_api.error.errors");
                            }
                        }
                    }
            );
        });
    }
}