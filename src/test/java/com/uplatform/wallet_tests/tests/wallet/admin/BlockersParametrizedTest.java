package com.uplatform.wallet_tests.tests.wallet.admin;
import com.uplatform.wallet_tests.tests.base.BaseParameterizedTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsPreventGambleSettedPayload;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsEventType;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.CRITICAL)
@Epic("CAP")
@Feature("PlayerBlockers")
@Suite("Позитивные сценарии: PlayerBlockers")
@Tag("Wallet") @Tag("СAP")
class BlockersParametrizedTest extends BaseParameterizedTest {

    private String platformNodeId;

    @BeforeAll
    void setupGlobalTestContext() {
        this.platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
    }

    static Stream<Arguments> blockersProvider() {
        return Stream.of(
                Arguments.of(true,  true,  "Гэмблинг и беттинг включены"),
                Arguments.of(true,  false, "Гэмблинг включен, беттинг выключен"),
                Arguments.of(false, true,  "Гэмблинг выключен, беттинг включен"),
                Arguments.of(false, false, "Гэмблинг и беттинг выключены")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("blockersProvider")
    @DisplayName("Позитивный сценарий совершения блокировки игрока:")
    void shouldRegisterPlayerAndUpdateBlockers(
            boolean gamblingEnabled,
            boolean bettingEnabled,
            String description) {
        final class TestContext {
            RegisteredPlayerData registeredPlayer;
            UpdateBlockersRequest updateBlockersRequest;
            NatsMessage<NatsPreventGambleSettedPayload> updateBlockersEvent;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("CAP API: Обновление блокировок", () -> {
            ctx.updateBlockersRequest = UpdateBlockersRequest.builder()
                    .gamblingEnabled(gamblingEnabled)
                    .bettingEnabled(bettingEnabled)
                    .build();

            var response = capAdminClient.updateBlockers(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId,
                    ctx.updateBlockersRequest
            );
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap_api.update_blockers.status_code");
        });

        step("NATS: Проверка поступления события setting_prevent_gamble_setted", () -> {
            var subject = natsClient.buildWalletSubject(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    ctx.registeredPlayer.getWalletData().getWalletUUID()
            );

            var filter = (BiPredicate<NatsPreventGambleSettedPayload, String>) (payload, typeHeader) ->
                    NatsEventType.SETTING_PREVENT_GAMBLE_SETTED.getHeaderValue().equals(typeHeader)
                            && payload.isGamblingActive() == gamblingEnabled
                            && payload.isBettingActive() == bettingEnabled;

            ctx.updateBlockersEvent = natsClient.findMessageAsync(
                    subject,
                    NatsPreventGambleSettedPayload.class,
                    filter).get();

            assertAll(
                    () -> assertEquals(gamblingEnabled, ctx.updateBlockersEvent.getPayload().isGamblingActive(), "nats.update_blockers.gambling_enabled"),
                    () -> assertEquals(bettingEnabled, ctx.updateBlockersEvent.getPayload().isBettingActive(), "nats.update_blockers.betting_enabled"),
                    () -> assertNotNull(ctx.updateBlockersEvent.getPayload().getCreatedAt(), "nats.update_blockers.created_at")
            );
        });

        step("DB Wallet: Проверка флагов в таблице wallet", () -> {
            var wallet = walletDatabaseClient.findWalletByUuidOrFail(
                    ctx.registeredPlayer.getWalletData().getWalletUUID()
            );
            assertAll(
                    () -> assertEquals(gamblingEnabled, wallet.isGamblingActive(), "db.wallet.gambling_active"),
                    () -> assertEquals(bettingEnabled, wallet.isBettingActive(), "db.wallet.betting_active")
            );
        });

        step("Redis(Wallet): Проверка флагов активности в Redis", () -> {
            var aggregate = redisClient.getWalletDataWithSeqCheck(
                    ctx.registeredPlayer.getWalletData().getWalletUUID(),
                    (int) ctx.updateBlockersEvent.getSequence()
            );
            assertAll(
                    () -> assertEquals(gamblingEnabled, aggregate.isGamblingActive(), "redis.wallet.gambling_active"),
                    () -> assertEquals(bettingEnabled, aggregate.isBettingActive(), "redis.wallet.betting_active")
            );
        });

        step("CAP API: Проверка получения блокировок", () -> {
            var response = capAdminClient.getBlockers(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID(),
                    utils.getAuthorizationHeader(),
                    platformNodeId);
            assertNotNull(response.getBody(), "cap_api.get_blockers.body_not_null");
            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "cap_api.get_blockers.status_code"),
                    () -> assertEquals(gamblingEnabled, response.getBody().isGamblingEnabled(), "cap_api.get_blockers.gambling_enabled"),
                    () -> assertEquals(bettingEnabled, response.getBody().isBettingEnabled(), "cap_api.get_blockers.betting_enabled")
            );
        });

        step("Kafka: Проверка поступления сообщения setting_prevent_gamble_setted в топик wallet.v8.projectionSource", () -> {
            var kafkaMsg = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
                    ctx.updateBlockersEvent.getSequence());
            assertTrue(utils.areEquivalent(kafkaMsg, ctx.updateBlockersEvent), "kafka.payload");
        });
    }
}