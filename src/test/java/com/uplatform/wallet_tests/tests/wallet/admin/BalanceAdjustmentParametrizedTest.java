package com.uplatform.wallet_tests.tests.wallet.admin;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.kafka.client.WalletProjectionKafkaClient;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.nats.dto.NatsBalanceAdjustedPayload;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import io.qameta.allure.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired private CapAdminClient capAdminClient;
    @Autowired private WalletProjectionKafkaClient walletProjectionKafkaClient;
    @Autowired private WalletRedisClient redisClient;
    @Autowired private NatsClient natsClient;
    @Autowired private TestUtils utils;
    @Autowired private DefaultTestSteps defaultTestSteps;
    @Autowired private EnvironmentConfigurationProvider configProvider;

    private final BigDecimal initialBalance = new BigDecimal("150.00");
    private final BigDecimal adjustmentAmount = new BigDecimal("100.00");
    private String defaultCurrency;
    private String platformNodeId;

    @BeforeAll
    void setupGlobalTestData() {
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
        final class TestData extends BaseParameterizedTest {
            RegisteredPlayerData registeredPlayer;
            CreateBalanceAdjustmentRequest adjustmentRequest;
            NatsMessage<NatsBalanceAdjustedPayload> balanceAdjustedEvent;
            WalletProjectionMessage projectionAdjustEvent;
            BigDecimal expectedBalanceAfterAdjustment;
        }
        final TestData testData = new TestData();

        testData.expectedBalanceAfterAdjustment = (direction == DirectionType.DECREASE)
                ? initialBalance.subtract(adjustmentAmount)
                : initialBalance.add(adjustmentAmount);

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer(initialBalance);
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("CAP API: Выполнение тестовой корректировки баланса - " + description, () -> {
            testData.adjustmentRequest = CreateBalanceAdjustmentRequest.builder()
                    .currency(defaultCurrency)
                    .amount(adjustmentAmount)
                    .reason(reasonType)
                    .operationType(operationType)
                    .direction(direction)
                    .comment(get(NAME))
                    .build();

            var response = capAdminClient.createBalanceAdjustment(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                    testData.adjustmentRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.create_balance_adjustment.status_code");
        });

        step("NATS: Проверка поступления события balance_adjusted", () -> {
            var subject = natsClient.buildWalletSubject(
                    testData.registeredPlayer.getWalletData().getPlayerUUID(),
                    testData.registeredPlayer.getWalletData().getWalletUUID());

            var filter = (BiPredicate<NatsBalanceAdjustedPayload, String>) (payload, typeHeader) -> {
                if (!NatsEventType.BALANCE_ADJUSTED.getHeaderValue().equals(typeHeader)) {
                    return false;
                }
                return payload.getComment().equals(testData.adjustmentRequest.getComment());
            };

            testData.balanceAdjustedEvent = natsClient.findMessageAsync(
                    subject,
                    NatsBalanceAdjustedPayload.class,
                    filter).get();

            var expectedAdjustment = (direction == DirectionType.DECREASE)
                    ? adjustmentAmount.negate() : adjustmentAmount;

            assertAll(
                    () -> assertEquals(testData.adjustmentRequest.getCurrency(), testData.balanceAdjustedEvent.getPayload().getCurrency(), "nats.balance_adjusted.currency"),
                    () -> assertNotNull(testData.balanceAdjustedEvent.getPayload().getUuid(), "nats.balance_adjusted.uuid"),
                    () -> assertEquals(0, expectedAdjustment.compareTo(testData.balanceAdjustedEvent.getPayload().getAmount()), "nats.balance_adjusted.amount"),
                    () -> assertEquals(mapOperationTypeToNatsInt(testData.adjustmentRequest.getOperationType()), testData.balanceAdjustedEvent.getPayload().getOperationType(), "nats.balance_adjusted.operation_type"),
                    () -> assertEquals(mapDirectionToNatsInt(testData.adjustmentRequest.getDirection()), testData.balanceAdjustedEvent.getPayload().getDirection(), "nats.balance_adjusted.direction"),
                    () -> assertEquals(mapReasonToNatsInt(testData.adjustmentRequest.getReason()), testData.balanceAdjustedEvent.getPayload().getReason(), "nats.balance_adjusted.reason"),
                    () -> assertEquals(testData.adjustmentRequest.getComment(), testData.balanceAdjustedEvent.getPayload().getComment(), "nats.balance_adjusted.comment"),
                    () -> assertNotNull(testData.balanceAdjustedEvent.getPayload().getUserUuid(), "nats.balance_adjusted.user_uuid"),
                    () -> assertNotNull(testData.balanceAdjustedEvent.getPayload().getUserName(), "nats.balance_adjusted.user_name")
            );
        });

        step("Redis: Проверка данных кошелька после корректировки", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    testData.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) testData.balanceAdjustedEvent.getSequence());
            assertAll(
                    () -> assertEquals(0, testData.expectedBalanceAfterAdjustment.compareTo(aggregate.getBalance()), "redis.wallet.balance")
            );
        });

        step("Kafka: Проверка поступления сообщения balance_adjusted в топик wallet.v8.projectionSource", () -> {
            testData.projectionAdjustEvent = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    testData.balanceAdjustedEvent.getSequence());
            assertTrue(utils.areEquivalent(testData.projectionAdjustEvent, testData.balanceAdjustedEvent), "kafka.payload");
        });
    }
}