package com.uplatform.wallet_tests.tests.wallet.betting.win;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
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

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.NOT_FOUND;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.generateBigDecimalAmount;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Негативные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
/**
 * Отказ в win для несуществующей ставки.
 *
 * Тест отправляет win с произвольным betId и ожидает код NOT_FOUND.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> новый пользователь.</li>
 *   <li><b>Основное действие:</b> запрос win для случайного betId.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и NOT_FOUND.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class WinForNonExistingBetTest extends BaseTest {

    @Test
    @DisplayName("Попытка зарегистрировать выигрыш для несуществующей ставки iframe")
    void shouldRejectWinForNonExistingBet() {
        final BigDecimal adjustmentAmount = new BigDecimal("100.00");
        final BigDecimal winAmount = generateBigDecimalAmount(adjustmentAmount);
        final Long nonExistingBetId = System.currentTimeMillis();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Попытка зарегистрировать выигрыш для несуществующей ставки", () -> {
            var winInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.WIN)
                    .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .summ(winAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .betId(nonExistingBetId)
                    .build();

            var winRequestBody = generateRequest(winInputData);
            var response = managerClient.makePayment(winRequestBody);

            assertAll("Проверка статус-кода и тела ответа при попытке выигрыша для несуществующей ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.win_non_existing.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.win_non_existing.body_is_null"),
                    () -> assertFalse(response.getBody().isSuccess(), "manager_api.win_non_existing.body.success"),
                    () -> assertEquals(NOT_FOUND.getDescription(), response.getBody().getDescription(), "manager_api.win_non_existing.body.description"),
                    () -> assertEquals(NOT_FOUND.getCode(), response.getBody().getErrorCode(), "manager_api.win_non_existing.body.errorCode")
            );
        });
    }
}