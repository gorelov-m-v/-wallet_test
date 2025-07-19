package com.uplatform.wallet_tests.tests.wallet.gambling.session;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGame;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGameSession;
import com.uplatform.wallet_tests.api.db.entity.core.GameProvider;
import com.uplatform.wallet_tests.api.db.entity.core.GameSessionMode;
import com.uplatform.wallet_tests.api.db.entity.wallet.WalletGameSession;
import com.uplatform.wallet_tests.api.http.fapi.dto.get_games.FapiGame;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameResponseBody;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import io.qameta.allure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Random;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Severity(SeverityLevel.BLOCKER)
@Epic("Gambling")
@Feature("StartGameSession")
@Suite("Позитивные сценарии: StartGameSession")
@Tag("Gambling") @Tag("Wallet") @Tag("Platform")
class StartGameSessionTest extends BaseTest {

    private static final Random RANDOM = new Random();

    @Test
    @DisplayName("Старт реальной игровой сессии для дефолтного кошелька без бонуса")
    void shouldRegisterPlayerLaunchGameAndVerifySession() {
        final String platformNodeId = configProvider.getEnvironmentConfig().getPlatform().getNodeId();
        final String secretKey = configProvider.getEnvironmentConfig().getApi().getManager().getSecret();

        final  class TestContext {
            RegisteredPlayerData registeredPlayer;
            ResponseEntity<LaunchGameResponseBody> launchResponse;
            CoreGameSession coreGameSession;
            WalletGameSession walletGameSession;
            LaunchGameRequestBody launchGameRequestBody;
            CoreGame coreGame;
            FapiGame fapiGame;
            GameProvider gameProvider;
        }
        final TestContext ctx = new TestContext();

        step("Default Step: Регистрация нового пользователя", () -> {
            ctx.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(ctx.registeredPlayer, "default_step.registration");
        });

        step("Public API: Получение списка игр и выбор случайной", () -> {
            var response = publicClient.getGames(1, 5);
            var games = response.getBody().getGames();
            ctx.fapiGame = games.get(RANDOM.nextInt(games.size()));
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_games.status_code");
        });

        step("Public API: Запуск выбранной игры", () -> {
            ctx.launchGameRequestBody = LaunchGameRequestBody.builder()
                    .language("en")
                    .returnUrl("https://beta-09.b2bdev.pro/casino")
                    .build();

            ctx.launchResponse = publicClient.launchGame(
                    ctx.fapiGame.getAlias(),
                    ctx.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    ctx.launchGameRequestBody);

            assertEquals(HttpStatus.OK, ctx.launchResponse.getStatusCode(), "fapi.launch_game.status_code");
        });

        step("DB Core: Получение данных игровой сессии из БД Core", () -> {
            ctx.coreGameSession = coreDatabaseClient.findLatestGameSessionByPlayerUuidOrFail(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID());

            var coreWallet = coreDatabaseClient.findWalletByIdOrFail(ctx.coreGameSession.getWalletId());
            ctx.coreGame = coreDatabaseClient.findGameByIdOrFail(ctx.coreGameSession.getGameId());
            ctx.gameProvider = coreDatabaseClient.findGameProviderByIdOrFail(ctx.coreGame.getGameProviderId());

            assertAll(
                    () -> assertNotNull(ctx.coreGameSession.getId(), "db.core_game_session.id"),
                    () -> assertEquals(coreWallet.getId(), ctx.coreGameSession.getWalletId(), "db.core_game_session.wallet_id"),
                    () -> assertEquals(ctx.coreGame.getId(), ctx.coreGameSession.getGameId(), "db.core_game_session.game_id"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getPlayerUUID(), ctx.coreGameSession.getPlayerUuid(), "db.core_game_session.player_uuid"),
                    () -> assertEquals(GameSessionMode.REAL.getId(), ctx.coreGameSession.getModeId(), "db.core_game_session.mode_id"),
                    () -> assertNotNull(ctx.coreGameSession.getPlayerIp(), "db.core_game_session.player_ip"),
                    () -> assertEquals(ctx.launchGameRequestBody.getLanguage(), ctx.coreGameSession.getLanguage(), "db.core_game_session.language"),
                    () -> assertEquals(ctx.launchGameRequestBody.getReturnUrl(), ctx.coreGameSession.getReturnUrl(), "db.core_game_session.return_url"),
                    () -> assertNotNull(ctx.coreGameSession.getStartedAt(), "db.core_game_session.started_at"),
                    () -> assertNotNull(ctx.coreGameSession.getUuid(), "db.core_game_session.uuid"),
                    () -> assertNotNull(ctx.coreGameSession.getUserAgent(), "db.core_game_session.user_agent"),
                    () -> assertNull(ctx.coreGameSession.getPlayerBonusId(), "db.core_game_session.player_bonus_id"),
                    () -> assertEquals(platformNodeId, ctx.coreGameSession.getNodeUuid(), "db.core_game_session.node_uuid"),
                    () -> assertNull(ctx.coreGameSession.getParentSessionUuid(), "db.core_game_session.parent_session_uuid"),
                    () -> assertNull(ctx.coreGameSession.getConversionCurrency(), "db.core_game_session.conversion_currency")
            );
        });

        step("Kafka: Проверка поступления события GameSessionStart", () -> {
            var kafkaMessage = gameSessionKafkaClient.expectGameSessionStartMessage(
                    ctx.coreGameSession.getUuid()
            );

            assertAll(
                    () -> assertEquals("wallet.gameSession", kafkaMessage.getMessage().getEventType(), "kafka.game_session.event_type"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getPlayerUUID(), kafkaMessage.getPlayerId(), "kafka.game_session.player_id"),
                    () -> assertTrue(kafkaMessage.getPlayerBonusUuid().isEmpty(), "kafka.game_session.player_bonus_uuid"),
                    () -> assertEquals(platformNodeId, kafkaMessage.getNodeId(), "kafka.game_session.node_id"),
                    () -> assertEquals(ctx.coreGameSession.getUuid(), kafkaMessage.getId(), "kafka.game_session.id"),
                    () -> assertEquals(ctx.coreGameSession.getPlayerIp(), kafkaMessage.getIp(), "kafka.game_session.ip"),
                    () -> assertEquals(ctx.gameProvider.getUuid(), kafkaMessage.getProviderId(), "kafka.game_session.provider_id"),
                    () -> assertEquals(ctx.gameProvider.getExternalUuid(), kafkaMessage.getProviderExternalId(), "kafka.game_session.provider_external_id"),
                    () -> assertNotNull(kafkaMessage.getGameTypeName(), "kafka.game_session.game_type_name"),
                    () -> assertEquals(ctx.coreGame.getUuid(), kafkaMessage.getGameId(), "kafka.game_session.game_id"),
                    () -> assertEquals(ctx.coreGame.getExternalUuid(), kafkaMessage.getGameExternalId(), "kafka.game_session.game_external_id"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getCurrency(), kafkaMessage.getCurrency(), "kafka.game_session.currency"),
                    () -> assertNotNull(kafkaMessage.getStartDate(), "kafka.game_session.start_date"),
                    () -> assertEquals(GameSessionMode.REAL.getName(), kafkaMessage.getGameMode(), "kafka.game_session.game_mode"),
                    () -> assertEquals(ctx.coreGameSession.getUserAgent(), kafkaMessage.getUseragent(), "kafka.game_session.user_agent"),
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getWalletUUID(), kafkaMessage.getWalletUuid(), "kafka.game_session.wallet_uuid"),
                    () -> assertEquals(secretKey, kafkaMessage.getSecretKey(), "kafka.game_session.secret_key"),
                    () -> assertNotNull(kafkaMessage.getTypeId(), "kafka.game_session.type_id"),
                    () -> assertNotNull(kafkaMessage.getCategoryId(), "kafka.game_session.category_id")
            );
        });

        step("DB Wallet: Получение данных игровой сессии из БД", () -> {
            ctx.walletGameSession = walletDatabaseClient.findSingleGameSessionByPlayerUuidOrFail(
                    ctx.registeredPlayer.getWalletData().getPlayerUUID());

            assertAll(
                    () -> assertEquals(ctx.registeredPlayer.getWalletData().getWalletUUID(), ctx.walletGameSession.getWalletUuid(), "db.wallet_game_session.wallet_uuid"),
                    () -> assertEquals(ctx.coreGameSession.getUuid(), ctx.walletGameSession.getGameSessionUuid(), "db.wallet_game_session.game_session_uuid"),
                    () -> assertEquals(secretKey, ctx.walletGameSession.getSecretKey(), "db.wallet_game_session.secret_key"),
                    () -> assertTrue(ctx.walletGameSession.getPlayerBonusUuid().isEmpty(), "db.wallet_game_session.player_bonus_uuid"),
                    () -> assertEquals(ctx.gameProvider.getUuid(), ctx.walletGameSession.getProviderUuid(), "db.wallet_game_session.provider_uuid"),
                    () -> assertEquals(ctx.gameProvider.getExternalUuid(), ctx.walletGameSession.getProviderExternalUuid(), "db.wallet_game_session.provider_external_uuid"),
                    () -> assertEquals(ctx.coreGame.getUuid(), ctx.walletGameSession.getGameUuid(), "db.wallet_game_session.game_uuid"),
                    () -> assertEquals(ctx.coreGame.getExternalUuid(), ctx.walletGameSession.getGameExternalUuid(), "db.wallet_game_session.game_external_uuid"),
                    () -> assertNotNull(ctx.walletGameSession.getTypeUuid(), "db.wallet_game_session.type_uuid"),
                    () -> assertNotNull(ctx.walletGameSession.getCategoryUuid(), "db.wallet_game_session.category_uuid"),
                    () -> assertEquals(platformNodeId, ctx.walletGameSession.getNodeUuid(), "db.wallet_game_session.node_uuid"),
                    () -> assertTrue(ctx.walletGameSession.getGameCurrency().isEmpty(), "db.wallet_game_session.game_currency")
            );
        });
    }
}