package com.uplatform.wallet_tests.tests.wallet.betting.bet;
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

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.ALREADY_PROCESSED_REQUEST_ID;
import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
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
 * Проверяет идемпотентность ставки по betId.
 *
 * Тест делает успешную ставку с определённым betId, а затем повторяет запрос
 * с тем же идентификатором и ожидает ошибку ALREADY_PROCESSED_REQUEST_ID.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя.</li>
 *   <li><b>Основное действие:</b> успешная ставка с уникальным betId.</li>
 *   <li><b>Основное действие:</b> повторная ставка с тем же betId.</li>
 *   <li><b>Проверка ответа API:</b> вторая ставка завершается ошибкой ALREADY_PROCESSED_REQUEST_ID.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class BetWithDuplicateBetIdTest extends BaseTest {

    @Test
    @DisplayName("Совершение ставки в iframe с неуникальным betId (проверка идемпотентности по ID)")
    void shouldRejectBetWithDuplicateBetId() {
        final BigDecimal adjustmentAmount = new BigDecimal("200.00");
        final BigDecimal betAmount = generateBigDecimalAmount(adjustmentAmount);
        final Long sharedBetId = System.currentTimeMillis();

        final class TestContext {
            RegisteredPlayerData registeredPlayer;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение первой (успешной) ставки на спорт с уникальным betId", () -> {
            var firstBetInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .betId(sharedBetId)
                    .build();

            var firstBetRequestBody = generateRequest(firstBetInputData);
            var response = managerClient.makePayment(firstBetRequestBody);

            assertAll("Проверка статус-кода и тела ответа первой (успешной) ставки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.first_bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.first_bet.body_is_null"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.first_bet.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.first_bet.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.first_bet.body.description")
            );
        });

        step("Manager API: Попытка совершения второй ставки с тем же betId", () -> {
            var secondBetInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .betId(sharedBetId)
                    .build();

            var secondBetRequestBody = generateRequest(secondBetInputData);
            var response = managerClient.makePayment(secondBetRequestBody);

            assertAll("Проверка статус-кода и тела ответа второй (неуспешной) ставки из-за дубликата betId",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.second_bet.status_code"),
                    () -> assertNotNull(response.getBody(), "manager_api.second_bet.body_is_null"),
                    () -> assertFalse(response.getBody().isSuccess(), "manager_api.second_bet.body.success"),
                    () -> assertEquals(ALREADY_PROCESSED_REQUEST_ID.getDescription(), response.getBody().getDescription(), "manager_api.second_bet.body.description"),
                    () -> assertEquals(ALREADY_PROCESSED_REQUEST_ID.getCode(), response.getBody().getErrorCode(), "manager_api.second_bet.body.errorCode")
            );
        });
    }
}