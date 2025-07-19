package com.uplatform.wallet_tests.tests.wallet.admin;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsBalanceAdjustedPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static com.uplatform.wallet_tests.tests.util.utils.NatsEnumMapper.*;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.NAME;
import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.get;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("BalanceAdjustment")
@Suite("Позитивные сценарии: BalanceAdjustment")
@Tag("Wallet") @Tag("CAP")
class BalanceAdjustmentParametrizedTest extends BaseParameterizedTest {

    private final BigDecimal initialBalance = new BigDecimal("150.00");
    private final BigDecimal adjustmentAmount = new BigDecimal("100.00");
    private String defaultCurrency;
    private String platformNodeId;

    @BeforeAll
    void setupGlobalTestContext() {
        this.defaultCurrency = configProvider.getEnvironmentConfig().getPlatform().getCurrency();
        this.platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
    }

    static Stream<Arguments> balanceAdjustmentScenariosProvider() {
        return Stream.of(
                arguments(DirectionType.INCREASE, OperationType.CORRECTION, ReasonType.MALFUNCTION, "Корректировка из-за технического сбоя"),
                arguments(DirectionType.INCREASE, OperationType.DEPOSIT, ReasonType.OPERATIONAL_MISTAKE, "Депозит из-за операционной ошибки"),
                arguments(DirectionType.INCREASE, OperationType.GIFT, ReasonType.BALANCE_CORRECTION, "Подарок для корректировки баланса"),
                arguments(DirectionType.INCREASE, OperationType.CASHBACK, ReasonType.OPERATIONAL_MISTAKE, "Кэшбэк из-за операционной ошибки"),
                arguments(DirectionType.INCREASE, OperationType.TOURNAMENT_PRIZE, ReasonType.MALFUNCTION, "Приз турнира из-за технического сбоя"),
                arguments(DirectionType.INCREASE, OperationType.JACKPOT, ReasonType.BALANCE_CORRECTION, "Джекпот для корректировки баланса"),
                arguments(DirectionType.DECREASE, OperationType.CORRECTION, ReasonType.BALANCE_CORRECTION, "Уменьшение для корректировки баланса"),
                arguments(DirectionType.DECREASE, OperationType.WITHDRAWAL, ReasonType.OPERATIONAL_MISTAKE, "Вывод из-за операционной ошибки"),
                arguments(DirectionType.DECREASE, OperationType.GIFT, ReasonType.MALFUNCTION, "Отмена подарка из-за технического сбоя"),
                arguments(DirectionType.DECREASE, OperationType.REFERRAL_COMMISSION, ReasonType.OPERATIONAL_MISTAKE, "Отмена реферальной комиссии из-за ошибки"),
                arguments(DirectionType.DECREASE, OperationType.TOURNAMENT_PRIZE, ReasonType.BALANCE_CORRECTION, "Корректировка выигрыша в турнире"),
                arguments(DirectionType.DECREASE, OperationType.JACKPOT, ReasonType.MALFUNCTION, "Отмена джекпота из-за технического сбоя")
        );
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("balanceAdjustmentScenariosProvider")
    @DisplayName("Позитивный сценарий создания корректировки средств:")
    void balanceAdjustmentTest(
            DirectionType direction,
            OperationType operationType,
            ReasonType reasonType,
            String description
    ) {
        final class TestContext extends BaseParameterizedTest {
            RegisteredPlayerData registeredPlayer;
            CreateBalanceAdjustmentRequest adjustmentRequest;
            NatsMessage<NatsBalanceAdjustedPayload> balanceAdjustedEvent;
            WalletProjectionMessage projectionAdjustEvent;
            BigDecimal expectedBalanceAfterAdjustment;
        }
        final TestContext ctx = new TestContext();

        ctx.expectedBalanceAfterAdjustment = (direction == DirectionType.DECREASE)
                ? initialBalance.subtract(adjustmentAmount)
                : initialBalance.add(adjustmentAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer(initialBalance);
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("CAP API: Выполнение тестовой корректировки баланса - " + description, () -> {
            ctx.adjustmentRequest = CreateBalanceAdjustmentRequest.builder()
                    .currency(defaultCurrency)
                    .amount(adjustmentAmount)
                    .reason(reasonType)
                    .operationType(operationType)
                    .direction(direction)
                    .comment(get(NAME))
                    .build();

            var response = capAdminClient.createBalanceAdjustment(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                    ctx.adjustmentRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.create_balance_adjustment.status_code");
        });

        step("NATS: Проверка поступления события balance_adjusted", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID());

            var filter = (BiPredicate<NatsBalanceAdjustedPayload, String>) (payload, typeHeader) -> {
                if (!NatsEventType.BALANCE_ADJUSTED.getHeaderValue().equals(typeHeader)) {
                    return false;
                }
                return payload.getComment().equals(ctx.adjustmentRequest.getComment());
            };

            ctx.balanceAdjustedEvent = natsClient.findMessageAsync(
                    subject,
                    NatsBalanceAdjustedPayload.class,
                    filter).get();

            var expectedAdjustment = (direction == DirectionType.DECREASE)
                    ? adjustmentAmount.negate() : adjustmentAmount;

            assertAll(
                    () -> assertEquals(ctx.adjustmentRequest.getCurrency(), ctx.balanceAdjustedEvent.getPayload().getCurrency(), "nats.balance_adjusted.currency"),
                    () -> assertNotNull(ctx.balanceAdjustedEvent.getPayload().getUuid(), "nats.balance_adjusted.uuid"),
                    () -> assertEquals(0, expectedAdjustment.compareTo(ctx.balanceAdjustedEvent.getPayload().getAmount()), "nats.balance_adjusted.amount"),
                    () -> assertEquals(mapOperationTypeToNatsInt(ctx.adjustmentRequest.getOperationType()), ctx.balanceAdjustedEvent.getPayload().getOperationType(), "nats.balance_adjusted.operation_type"),
                    () -> assertEquals(mapDirectionToNatsInt(ctx.adjustmentRequest.getDirection()), ctx.balanceAdjustedEvent.getPayload().getDirection(), "nats.balance_adjusted.direction"),
                    () -> assertEquals(mapReasonToNatsInt(ctx.adjustmentRequest.getReason()), ctx.balanceAdjustedEvent.getPayload().getReason(), "nats.balance_adjusted.reason"),
                    () -> assertEquals(ctx.adjustmentRequest.getComment(), ctx.balanceAdjustedEvent.getPayload().getComment(), "nats.balance_adjusted.comment"),
                    () -> assertNotNull(ctx.balanceAdjustedEvent.getPayload().getUserUuid(), "nats.balance_adjusted.user_uuid"),
                    () -> assertNotNull(ctx.balanceAdjustedEvent.getPayload().getUserName(), "nats.balance_adjusted.user_name")
            );
        });

        step("Redis: Проверка данных кошелька после корректировки", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.balanceAdjustedEvent.getSequence());
            assertAll(
                    () -> assertEquals(0, ctx.expectedBalanceAfterAdjustment.compareTo(aggregate.getBalance()), "redis.wallet.balance")
            );
        });

        step("Kafka: Проверка поступления сообщения balance_adjusted в топик wallet.v8.projectionSource", () -> {
            ctx.projectionAdjustEvent = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.balanceAdjustedEvent.getSequence());
            assertTrue(utils.areEquivalent(ctx.projectionAdjustEvent, ctx.balanceAdjustedEvent), "kafka.payload");
        });
    }
}