package com.uplatform.wallet_tests.tests.wallet.registration;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Отладочный тест полной регистрации игрока с KYC.
 *
 * Позволяет проверить корректность выполнения нового дефолтного шага
 * {@link DefaultTestSteps#registerNewPlayerWithKyc()} без дополнительных действий.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> выполнение шага полной регистрации с KYC.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: FAPI</li>
 *   <li>Kafka</li>
 *   <li>Redis</li>
 * </ul>
 * @see com.uplatform.wallet_tests.api.http.fapi.client.FapiClient
 *
 */
@Severity(SeverityLevel.NORMAL)
@Epic("Registration")
@Feature("FullRegistrationWithKyc")
@Suite("Отладка: FullRegistrationWithKyc")
@Tag("Wallet2")
class FullRegistrationDebugTest extends BaseTest {

    @Test
    @DisplayName("Полная регистрация игрока с KYC")
    void shouldRegisterPlayerWithKyc() {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Полная регистрация игрока с KYC", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayerWithKyc();
            assertNotNull(ctx.registeredPlayer, "default_step.registration_with_kyc");
        });
    }
}
