package com.uplatform.wallet_tests.tests.wallet.betting.bet;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponCalcStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponType;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.model.enums.IFrameRecordType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.MakePaymentData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import static com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode.SUCCESS;
import static com.uplatform.wallet_tests.tests.util.utils.MakePaymentRequestGenerator.generateRequest;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("Betting")
@Feature("MakePayment")
@Suite("Позитивные сценарии: MakePayment")
@Tag("Betting") @Tag("Wallet")
/**
 * Проверяет обработку ставки, совершённой из iframe, и фиксацию всех сопутствующих событий.
 *
 * Тест имитирует совершение ставки новым игроком через API MakePayment и убеждается,
 * что событие betted_from_iframe корректно распространяется по системам и отражается в БД и Redis.
 *
 * <p><b>Сценарий теста:</b></p>
 * <ol>
 *   <li><b>Регистрация игрока:</b> создание нового пользователя и кошелька.</li>
 *   <li><b>Основное действие:</b> отправка запроса makePayment для ставки из iframe.</li>
 *   <li><b>Проверка ответа API:</b> статус код 200 и успешное тело.</li>
 *   <li><b>Проверка NATS:</b> получение события betted_from_iframe.</li>
 *   <li><b>Проверка Kafka:</b> сообщение в топике wallet.v8.projectionSource.</li>
 *   <li><b>Проверка БД:</b> обновление player_threshold_win.</li>
 *   <li><b>Проверка БД:</b> запись в betting_projection_iframe_history.</li>
 *   <li><b>Проверка Redis:</b> изменение агрегата кошелька.</li>
 * </ol>
 *
 * <p><b>Проверяемые компоненты и сущности:</b></p>
 * <ul>
 *   <li>REST API: makePayment</li>
 *   <li>NATS: событие betted_from_iframe</li>
 *   <li>Kafka: wallet.v8.projectionSource</li>
 *   <li>База данных Wallet</li>
 *   <li>Redis кошелька</li>
 * </ul>
 *
 * @see com.uplatform.wallet_tests.api.http.manager.client.ManagerClient
 */
class BetFromIframeTest extends BaseTest {

    @Test
    @DisplayName("Проверка обработки ставки iframe")
    void shouldPlaceBetFromIframeAndVerifyEvent() {
        final BigDecimal adjustmentAmount = new BigDecimal("150.00");
        final BigDecimal betAmount = new BigDecimal("10.15");
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            MakePaymentData betInputData;
            MakePaymentRequest betRequestBody;
            NatsMessage<NatsBettingEventPayload> betEvent;
            BigDecimal expectedBalance;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalance = adjustmentAmount.subtract(betAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(adjustmentAmount);

            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Manager API: Совершение ставки на спорт", () -> {
            ctx.betInputData = MakePaymentData.builder()
                    .type(NatsBettingTransactionOperation.BET)
                    .playerId(ctx.registeredPlayer.getWalletData().getPlayerUUID())
                    .summ(betAmount.toPlainString())
                    .couponType(NatsBettingCouponType.SINGLE)
                    .currency(ctx.registeredPlayer.getWalletData().getCurrency())
                    .build();

            ctx.betRequestBody = generateRequest(ctx.betInputData);

            var response = managerClient.makePayment(ctx.betRequestBody);

            assertAll("Проверка статус-кода и тела ответа",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "manager_api.status_code"),
                    () -> assertTrue(response.getBody().isSuccess(), "manager_api.body.success"),
                    () -> assertEquals(SUCCESS.getCode(), response.getBody().getErrorCode(), "manager_api.body.errorCode"),
                    () -> assertEquals(SUCCESS.getDescription(), response.getBody().getDescription(), "manager_api.body.description")
            );
        });

        step("NATS: Проверка поступления события betted_from_iframe", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            BiPredicate<NatsBettingEventPayload, String> filter = (payload, typeHeader) ->
                    NatsEventType.BETTED_FROM_IFRAME.getHeaderValue().equals(typeHeader) &&
                            Objects.equals(ctx.betRequestBody.getBetId(), payload.getBetId());

            ctx.betEvent = natsClient.findMessageAsync(
                    subject,
                    NatsBettingEventPayload.class,
                    filter).get();

            var actualPayload = ctx.betEvent.getPayload();
            var expectedBetInfoList = objectMapper.readValue(
                    ctx.betRequestBody.getBetInfo(),
                    new TypeReference<List<NatsBettingEventPayload.BetInfoDetail>>() {});
            assertAll("Проверка основных полей NATS payload",
                    () -> assertNotNull(actualPayload.getUuid(), "nats.payload.uuid"),
                    () -> assertEquals(ctx.betRequestBody.getType(), actualPayload.getType(), "nats.payload.type"),
                    () -> assertEquals(ctx.betRequestBody.getBetId(), actualPayload.getBetId(), "nats.payload.bet_id"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequestBody.getSumm()).negate().compareTo(actualPayload.getAmount()), "nats.payload.amount"),
                    () -> assertNotNull(actualPayload.getRawAmount(), "nats.payload.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequestBody.getSumm()).compareTo(actualPayload.getRawAmount()), "nats.payload.raw_amount"),
                    () -> assertEquals(0, new BigDecimal(ctx.betRequestBody.getTotalCoef()).compareTo(actualPayload.getTotalCoeff()), "nats.payload.total_coeff"),
                    () -> assertTrue(Math.abs(ctx.betRequestBody.getTime() - actualPayload.getTime()) <= 10, "nats.payload.time"),
                    () -> assertNotNull(actualPayload.getCreatedAt(), "nats.payload.created_at"),
                    () -> assertTrue(actualPayload.getWageredDepositInfo().isEmpty(), "nats.payload.wagered_deposit_info")
            );

            var expectedBetInfo = expectedBetInfoList.get(0);
            var actualBetInfo = actualPayload.getBetInfo().get(0);
            assertAll("Проверка полей внутри bet_info NATS payload",
                    () -> assertEquals(expectedBetInfo.getChampId(), actualBetInfo.getChampId(), "nats.payload.bet_info.champId"),
                    () -> assertEquals(expectedBetInfo.getChampName(), actualBetInfo.getChampName(), "nats.payload.bet_info.champ_name"),
                    () -> assertEquals(0, expectedBetInfo.getCoef().compareTo(actualBetInfo.getCoef()), "nats.payload.bet_info.coef"),
                    () -> assertEquals(expectedBetInfo.getCouponType(), actualBetInfo.getCouponType(), "nats.payload.bet_info.coupon_type"),
                    () -> assertEquals(expectedBetInfo.getDateStart(), actualBetInfo.getDateStart(), "nats.payload.bet_info.date_start"),
                    () -> assertEquals(expectedBetInfo.getEvent(), actualBetInfo.getEvent(), "nats.payload.bet_info.event"),
                    () -> assertEquals(expectedBetInfo.getGameName(), actualBetInfo.getGameName(), "nats.payload.bet_info.game_name"),
                    () -> assertEquals(expectedBetInfo.getScore(), actualBetInfo.getScore(), "nats.payload.bet_info.score"),
                    () -> assertEquals(expectedBetInfo.getSportName(), actualBetInfo.getSportName(), "nats.payload.bet_info.sport_name")
            );
        });

        step("Kafka: Проверка поступления сообщения betted_from_iframe в топик wallet.v8.projectionSource", () -> {
            var kafkaMessage = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.betEvent.getSequence());

            assertTrue(utils.areEquivalent(kafkaMessage, ctx.betEvent), "kafka.payload");
        });

        step("DB Wallet: Проверка записи порога выигрыша в player_threshold_win", () -> {
            var threshold = walletDatabaseClient.findThresholdByPlayerUuidOrFail(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID());

            var player = ctx.registeredPlayer.getWalletData();
            assertAll("Проверка трешхолда после совершения ставки на спорт",
                    () -> assertEquals(player.getPlayerUUID(), threshold.getPlayerUuid(), "db.threshold.player_uuid"),
                    () -> assertEquals(0, betAmount.negate().compareTo(threshold.getAmount()), "db.threshold.amount"),
                    () -> assertNotNull(threshold.getUpdatedAt(), "db.threshold.updated_at")
            );
        });

        step("DB Wallet: Проверка записи в таблице betting_projection_iframe_history", () -> {
            var dbTransaction = walletDatabaseClient.findLatestIframeHistoryByUuidOrFail(
                    ctx.betEvent.getPayload().getUuid());

            var betEvent = ctx.betEvent.getPayload();
            var player = ctx.registeredPlayer.getWalletData();
            var betInfo = betEvent.getBetInfo().get(0);

            var actualDbBetInfoList = objectMapper
                    .readValue(dbTransaction.getBetInfo(),
                            new TypeReference<List<NatsBettingEventPayload.BetInfoDetail>>() {});

            assertAll("Проверка записанной строки в таблицу с историей ставок на спорт",
                    () -> assertEquals(betEvent.getUuid(), dbTransaction.getUuid(), "db.iframe_history.uuid"),
                    () -> assertEquals(player.getWalletUUID(), dbTransaction.getWalletUuid(), "db.iframe_history.wallet_uuid"),
                    () -> assertEquals(player.getPlayerUUID(), dbTransaction.getPlayerUuid(), "db.iframe_history.player_uuid"),
                    () -> assertEquals(CouponType.SINGLE, dbTransaction.getCouponType(), "db.iframe_history.coupon_type"),
                    () -> assertEquals(CouponStatus.ACCEPTED, dbTransaction.getCouponStatus(),  "db.iframe_history.coupon_status"),
                    () -> assertEquals(CouponCalcStatus.NO, dbTransaction.getCouponCalcStatus(),  "db.iframe_history.coupon_calc_status"),
                    () -> assertEquals(betEvent.getBetId(), dbTransaction.getBetId(), "db.iframe_history.bet_id"),
                    () -> assertEquals(betEvent.getBetInfo(), actualDbBetInfoList, "db.iframe_history.bet_info"),
                    () -> assertEquals(0, betEvent.getAmount().compareTo(dbTransaction.getAmount().negate()), "db.iframe_history.amount"),
                    () -> assertEquals(0, betInfo.getCoef().compareTo(dbTransaction.getTotalCoeff()), "db.iframe_history.total_coeff"),
                    () -> assertNotNull(dbTransaction.getBetTime(), "db.iframe_history.bet_time"),
                    () -> assertNotNull(dbTransaction.getModifiedAt(), "db.iframe_history.modified_at"),
                    () -> assertNotNull(dbTransaction.getCreatedAt(), "db.iframe_history.created_at"),
                    () -> assertEquals(ctx.betEvent.getSequence(), dbTransaction.getSeq(), "db.iframe_history.seq"),
                    () -> assertNull(dbTransaction.getPrevCoeff(), "db.iframe_history.prev_coeff"),
                    () -> assertEquals(0, betInfo.getCoef().compareTo(dbTransaction.getSourceCoeff()), "db.iframe_history.source_coeff"),
                    () -> assertEquals(0, betEvent.getAmount().compareTo(dbTransaction.getAmountDelta()), "db.iframe_history.amount_delta"),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(dbTransaction.getWinSum()), "db.iframe_history.win_sum"),
                    () -> assertNotNull(dbTransaction.getCouponCreatedAt(), "db.iframe_history.coupon_created_at")
            );
        });

        step("Redis(Wallet): Получение и проверка полных данных кошелька", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.betEvent.getSequence());

            var actualBetInfo = aggregate.getIFrameRecords().get(0);
            var expectedBetInfo = ctx.betEvent.getPayload();

            assertAll("Проверка изменения агрегата, после обработки ставки",
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.getBalance()), "redis.aggregate.balance"),
                    () -> assertEquals(0, ctx.expectedBalance.compareTo(aggregate.getAvailableWithdrawalBalance()), "redis.aggregate.available_withdrawal_balance"),
                    () -> assertEquals(expectedBetInfo.getUuid(), actualBetInfo.getUuid(), "redis.aggregate.iframe.uuid"),
                    () -> assertEquals(expectedBetInfo.getBetId(), actualBetInfo.getBetID(), "redis.aggregate.iframe.bet_id"),
                    () -> assertEquals(expectedBetInfo.getAmount(), actualBetInfo.getAmount(), "redis.aggregate.iframe.amount"),
                    () -> assertEquals(0, expectedBetInfo.getTotalCoeff().compareTo(actualBetInfo.getTotalCoeff()), "redis.aggregate.iframe.total_coeff"),
                    () -> assertNotNull(actualBetInfo.getTime(), "redis.aggregate.iframe.time"),
                    () -> assertNotNull(actualBetInfo.getCreatedAt(), "redis.aggregate.iframe.created_at"),
                    () -> assertEquals(IFrameRecordType.BET, actualBetInfo.getType(), "redis.aggregate.iframe.type")
            );
        });
    }
}