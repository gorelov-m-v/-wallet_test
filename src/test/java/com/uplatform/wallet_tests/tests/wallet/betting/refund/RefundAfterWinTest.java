package com.uplatform.wallet_tests.tests.wallet.betting.refund;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Негативные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
/**
 * Рефанд после выплаты win.
 *
 * Сначала ставка выигрывает, затем применяется refund.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> новый пользователь.</li>
 *   <li><b>Основное действие:</b> ставка, win, затем refund.</li>
 *   <li><b>Проверка ответа API:</b> все шаги успешны.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class RefundAfterWinTest extends BaseTest {
    @Autowired private ManagerClient managerClient;
    @Autowired private DefaultTestSteps defaultTestSteps;

    @Test
    @DisplayName("Проверка обработки рефанда после расчета ставки Win -> Refund")
    void shouldProcessRefundAfterWin() {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal winAmount = new BigDecimal("20.15");

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
        }
        final TestData testData = new TestData();

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

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.body_not_null"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
            );
        });

        step("Manager API: Получение выигрыша", () -> {
            testData.betRequestBody.setSumm(winAmount.toString());
            testData.betRequestBody.setType(NatsBettingTransactionOperation.WIN);
            var response = managerClient.makePayment(testData.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.body_not_null"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
            );
        });

        step("Manager API: Получение рефанда", () -> {
            testData.betRequestBody.setSumm(betAmount.toString());
            testData.betRequestBody.setType(NatsBettingTransactionOperation.REFUND);
            var response = managerClient.makePayment(testData.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.body_not_null")
            );
        });
    }
}