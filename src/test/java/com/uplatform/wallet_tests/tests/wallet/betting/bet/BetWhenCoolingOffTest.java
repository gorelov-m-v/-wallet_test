package com.uplatform.wallet_tests.tests.wallet.betting.bet;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.fapi.dto.enums.RestrictionExpireType;
import com.uplatform.wallet_tests.api.http.fapi.dto.player_restrictions.PlayerRestrictionsRequest;
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

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.COOLING_OFF_LIMIT_REACHED;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Негативные сценарии: MakePayment")
@Tag("")
/**
 * Проверяет отказ в ставке при установленном режиме CoolingOff у игрока.
 *
 * После регистрации игроку через Public API назначается самоограничение
 * CoolingOff, затем попытка ставки должна завершиться ошибкой
 * COOLING_OFF_LIMIT_REACHED.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> установка CoolingOff через Public API и
 *   попытка сделать ставку.</li>
 *   <li><b>Проверка ответа API:</b> статус 200 и ошибка COOLING_OFF_LIMIT_REACHED.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>Public API: ограничения игрока</li>
 *   <li>REST API: makePayment</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class BetWhenCoolingOffTest extends BaseTest {

    @Test
    @DisplayName("Совершение ставки в iframe, игроком с самоограничением")
    void shouldRejectBetWhenCoolingOff() {
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

        step("FAPI: Установка самоограничения игрока", () -> {
            var request = new PlayerRestrictionsRequest(RestrictionExpireType.DAY);
            var response = publicClient.getPlayerRestrictions(
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    request
            );
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.restrictions.status_code");
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted during sleep", e);
            }

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
                    () -> assertEquals(COOLING_OFF_LIMIT_REACHED.getDescription(), response.getBody().getDescription(), "manager_api.body.description"),
                    () -> assertEquals(COOLING_OFF_LIMIT_REACHED.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode")
            );
        });
    }
}