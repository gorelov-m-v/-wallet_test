package com.uplatform.wallet_tests.tests.wallet.admin;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountResponse;
import com.uplatform.wallet_tests.api.nats.dto.BlockAmountRevokedEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("BlockAmount")
@Suite("Позитивные сценарии: DeleteBlockAmount")
@Tag("Wallet") @Tag("CAP")
class DeleteBlockAmountTest extends BaseTest {

    private String platformNodeId;

    @BeforeAll
    void setupGlobalTestData() {
        this.platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
    }

    @Test
    @DisplayName("Проверка удаления блокировки средств с кошелька игрока")
    void shouldDeleteBlockAmountAndVerifyResponse() {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal blockAmount = new BigDecimal("50.00");

        final class TestData {
            RegisteredPlayerData registeredPlayer;
            CreateBlockAmountRequest blockAmountRequest;
            ResponseEntity<CreateBlockAmountResponse> blockAmountResponse;
            ResponseEntity<Void> deleteBlockAmountResponse;
            NatsMessage<BlockAmountRevokedEventPayload> blockAmountRevokedEvent;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(BigDecimal.ZERO);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("CAP API: Корректировка баланса", () -> {
            var request = CreateBalanceAdjustmentRequest.builder()
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .amount(adjustmentAmount)
                    .reason(ReasonType.MALFUNCTION)
                    .operationType(OperationType.CORRECTION)
                    .direction(DirectionType.INCREASE)
                    .build();

            var response = capAdminClient.createBalanceAdjustment(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                    request);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.status_code");
        });

        step("CAP API: Создание блокировки средств", () -> {
            testData.blockAmountRequest = CreateBlockAmountRequest.builder()
                    .reason(get(NAME))
                    .currency(testData.registeredPlayer.getWalletData().getCurrency())
                    .amount(blockAmount.toString())
                    .build();

            testData.blockAmountResponse = capAdminClient.createBlockAmount(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    testData.blockAmountRequest
            );

            var responseBody = testData.blockAmountResponse.getBody();
            assertAll("Проверка данных в ответе на создание блокировки средств",
                    () -> assertEquals(HttpStatus.OK, testData.blockAmountResponse.getStatusCode(), "cap_api.block_amount.status_code"),
                    () -> assertNotNull(responseBody, "cap_api.block_amount.response_body_not_null"),
                    () -> assertNotNull(responseBody.getTransactionId(), "cap_api.block_amount.transaction_id")
            );
        });

        step("CAP API: Получение списка блокировок для проверки наличия созданной блокировки", () -> {
            var response = capAdminClient.getBlockAmountList(
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    testData.registeredPlayer.getWalletData().getPlayerUUID());
            assertNotNull(response.getBody(), "cap_api.get_block_amount_list.response_body_not_null");
            assertNotNull(testData.blockAmountResponse.getBody(), "cap_api.create_block_amount.response_body_not_null");

            var expectedTxId = testData.blockAmountResponse.getBody().getTransactionId();

            assertAll("Проверка наличия созданной блокировки в списке",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.status_code"),
                    () -> assertFalse(response.getBody().getItems().isEmpty(), "cap_api.block_amount_list.not_empty"),
                    () -> assertEquals(expectedTxId, response.getBody().getItems().get(0).getTransactionId(), "cap_api.block_amount_list.transaction_id")
            );
        });

        step("CAP API: Удаление блокировки средств", () -> {
            assertNotNull(testData.blockAmountResponse.getBody(), "cap_api.create_block_amount.response_body_not_null_before_delete");
            testData.deleteBlockAmountResponse = capAdminClient.deleteBlockAmount(
                    testData.blockAmountResponse.getBody().getTransactionId(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    testData.registeredPlayer.getWalletData().getPlayerUUID()
            );
            assertEquals(HttpStatus.NO_CONTENT, testData.deleteBlockAmountResponse.getStatusCode(), "cap_api.delete_block_amount.status_code");
        });

        step("CAP API: Проверка отсутствия удаленной блокировки в списке", () -> {
            var response = capAdminClient.getBlockAmountList(
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    testData.registeredPlayer.getWalletData().getPlayerUUID());
            assertNotNull(response.getBody(), "cap_api.get_block_amount_list_after_delete.response_body_not_null");
            assertNotNull(testData.blockAmountResponse.getBody(), "cap_api.create_block_amount.response_body_not_null_for_deleted_tx_id");

            var deletedTxId = testData.blockAmountResponse.getBody().getTransactionId();
            var blockStillExists = response.getBody().getItems().stream()
                    .anyMatch(item -> item.getTransactionId().equals(deletedTxId));

            assertAll("Проверка отсутствия удаленной блокировки",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.status_code"),
                    () -> assertFalse(blockStillExists, "cap_api.block_amount_list.transaction_not_exists")
            );
        });

        step("NATS: Проверка поступления события block_amount_revoked", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<BlockAmountRevokedEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BLOCK_AMOUNT_REVOKED.getHeaderValue().equals(typeHeader);

            testData.blockAmountRevokedEvent = natsClient.findMessageAsync(
                    subject,
                    BlockAmountRevokedEventPayload.class,
                    filter).get();

            var payload = testData.blockAmountRevokedEvent.getPayload();
            assertAll("Проверка полей в событии снятия блокировки",
                    () -> assertNotNull(payload.getUuid(), "nats.block_amount_revoked.uuid"),
                    () -> assertNotNull(payload.getUserUuid(), "nats.block_amount_revoked.user_uuid"),
                    () -> assertNotNull(payload.getUserName(), "nats.block_amount_revoked.user_name"),
                    () -> assertEquals(platformNodeId, payload.getNodeUuid(), "nats.block_amount_revoked.node_uuid")
            );
        });

        step("Kafka: Проверка поступления сообщения block_amount_revoked в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    testData.blockAmountRevokedEvent.getSequence());
            assertTrue(utils.areEquivalent(
                    kafkaMessage, testData.blockAmountRevokedEvent), "kafka.payload");
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.blockAmountRevokedEvent.getSequence());
            assertNotNull(testData.blockAmountResponse.getBody(), "cap_api.create_block_amount.response_body_not_null_for_redis_check");

            var expectedBalance = adjustmentAmount;
            var deletedBlockAmountId = testData.blockAmountResponse.getBody().getTransactionId();

            var blockedAmount = aggregate.getBlockedAmounts().stream()
                    .filter(block -> block.getUuid().equals(deletedBlockAmountId))
                    .findFirst();

            assertAll("Проверка агрегата после удаления блокировки",
                    () -> assertEquals((int) testData.blockAmountRevokedEvent.getSequence(), aggregate.getLastSeqNumber(), "redis.aggregate.last_seq_number"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.getBalance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, expectedBalance.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertEquals(0, adjustmentAmount.subtract(blockAmount).compareTo(aggregate.getBalanceBefore()), "redis.aggregate.balance_before"),
                    () -> assertTrue(blockedAmount.isPresent(), "redis.aggregate.blocked_amount.exists"),
                    () -> assertEquals(NatsBlockAmountStatus.REVOKED.getValue(), blockedAmount.get().getStatus(), "redis.aggregate.blocked_amount.status"),
                    () -> assertEquals(testData.blockAmountRequest.getReason(), blockedAmount.get().getReason(), "redis.aggregate.blocked_amount.reason"),
                    () -> assertEquals(0, blockAmount.negate().compareTo(blockedAmount.get().getAmount()), "redis.aggregate.blocked_amount.amount"),
                    () -> assertEquals(0, blockAmount.compareTo(blockedAmount.get().getDeltaAvailableWithdrawalBalance()), "redis.aggregate.blocked_amount.delta_available_withdrawal_balance")
            );
        });
    }
}