package com.uplatform.wallet_tests.tests.wallet.betting.loss;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Позитивные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
@TmsLink("")
/**
 * Проверяет loss при запрете гемблинга.
 *
 * После ставки гемблинг блокируется, но loss отправляется успешно.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> новый пользователь.</li>
 *   <li><b>Основное действие:</b> ставка, блокировка гемблинга и loss.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и успех.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>CAP API: updateBlockers</li>
 *   <li>REST API: makePayment</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class LossWhenGamblingBlockedTest {
    @Autowired private CapAdminClient capAdminClient;
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private TestUtils utils;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    @Test
    @DisplayName("Получение проигрыша в iframe, игроком с заблокированным гемблингом")
    void shouldProcessLossWhenOnlyGamblingIsBlocked() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal lossAmount = BigDecimal.ZERO;
        final class TestData {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            BigDecimal expectedBalance;
        }
        final TestData testData = new TestData();

        testData.expectedBalance = adjustmentAmount.subtract(betAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            testData.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(testData.registeredPlayer.getWalletData().getPlayerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .build();

            testData.betRequestBody = generateRequest(testData.betInputData);
            var response = managerClient.makePayment(testData.betRequestBody);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code");
        });

        step("CAP API: Блокировка гемблинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(false)
                    .bettingEnabled(true)
                    .build();

            var response = capAdminClient.updateBlockers(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.status_code");
        });

        step("Manager API: Получение проигрыша", () -> {
            testData.betRequestBody.setSumm(lossAmount.toString());
            testData.betRequestBody.setType(NatsBettingTransactionOperation.LOSS);
            var response = managerClient.makePayment(testData.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
            );
        });
    }
}