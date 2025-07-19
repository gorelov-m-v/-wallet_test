package com.uplatform.wallet_tests.tests.wallet.betting.bet;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.BETTING_IS_DISABLED;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Негативные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet") @Tag("Limits")
/**
 * Проверяет отказ в ставке при заблокированном беттинге у игрока.
 *
 * Сначала через CAP API беттинг игрока блокируется, после чего попытка
 * совершить ставку должна вернуть ошибку BETTING_IS_DISABLED.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> блокировка беттинга через CAP и попытка
 *   совершить ставку.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и ошибка BETTING_IS_DISABLED.</li>
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
class BetWhenBettingBlockedTest extends BaseTest {

    @Test
    @DisplayName("Совершение ставки в iframe, игроком с заблокированным беттингом")
    void shouldRejectBetWhenBettingIsBlocked() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final BigDecimal betAmount = new BigDecimal("10.15");
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("CAP API: Блокировка беттинга", () -> {
            var request = UpdateBlockersRequest.builder()
                    .gamblingEnabled(true)
                    .bettingEnabled(false)
                    .build();

            var response = capAdminClient.updateBlockers(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    request
            );
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.status_code");
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            var data = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .build();

            var request = generateRequest(data);

            var response = managerClient.makePayment(request);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertFalse(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(BETTING_IS_DISABLED.getDescription(), response.getBody().getDescription(), "manager_api.body.description"),
                    () -> assertEquals(BETTING_IS_DISABLED.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode")
            );
        });
    }
}