package com.uplatform.wallet_tests.tests.wallet.gambling.session;
import com.uplatform.wallet_tests.tests.base.BaseTest;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.allure.Suite;
import com.uplatform.wallet_tests.api.db.WalletDatabaseClient;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGame;
import com.uplatform.wallet_tests.api.db.entity.core.CoreGameSession;
import com.uplatform.wallet_tests.api.db.entity.core.GameProvider;
import com.uplatform.wallet_tests.api.db.entity.core.GameSessionMode;
import com.uplatform.wallet_tests.api.db.entity.wallet.WalletGameSession;
import com.uplatform.wallet_tests.api.http.fapi.dto.get_games.FapiGame;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameResponseBody;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
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

        final  class TestData {
            RegisteredPlayerData registeredPlayer;
            ResponseEntity<LaunchGameResponseBody> launchResponse;
            CoreGameSession coreGameSession;
            WalletGameSession walletGameSession;
            LaunchGameRequestBody launchGameRequestBody;
            CoreGame coreGame;
            FapiGame fapiGame;
            GameProvider gameProvider;
        }
        final TestData testData = new TestData();

        step("Default Step: Регистрация нового пользователя", () -> {
            testData.registeredPlayer = defaultTestSteps.registerNewPlayer();
            assertNotNull(testData.registeredPlayer, "default_step.registration");
        });

        step("Public API: Получение списка игр и выбор случайной", () -> {
            var response = publicClient.getGames(1, 5);
            var games = response.getBody().getGames();
            testData.fapiGame = games.get(RANDOM.nextInt(games.size()));
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_games.status_code");
        });

        step("Public API: Запуск выбранной игры", () -> {
            testData.launchGameRequestBody = LaunchGameRequestBody.builder()
                    .language("en")
                    .returnUrl("https://beta-09.b2bdev.pro/casino")
                    .build();

            testData.launchResponse = publicClient.launchGame(
                    testData.fapiGame.getAlias(),
                    testData.registeredPlayer.getAuthorizationResponse().getBody().getToken(),
                    testData.launchGameRequestBody);

            assertEquals(HttpStatus.OK, testData.launchResponse.getStatusCode(), "fapi.launch_game.status_code");
        });

        step("DB Core: Получение данных игровой сессии из БД Core", () -> {
            testData.coreGameSession = coreDatabaseClient.findLatestGameSessionByPlayerUuidOrFail(
                    testData.registeredPlayer.getWalletData().getPlayerUUID());

            var coreWallet = coreDatabaseClient.findWalletByIdOrFail(testData.coreGameSession.getWalletId());
            testData.coreGame = coreDatabaseClient.findGameByIdOrFail(testData.coreGameSession.getGameId());
            testData.gameProvider = coreDatabaseClient.findGameProviderByIdOrFail(testData.coreGame.getGameProviderId());

            assertAll(
                    () -> assertNotNull(testData.coreGameSession.getId(), "db.core_game_session.id"),
                    () -> assertEquals(coreWallet.getId(), testData.coreGameSession.getWalletId(), "db.core_game_session.wallet_id"),
                    () -> assertEquals(testData.coreGame.getId(), testData.coreGameSession.getGameId(), "db.core_game_session.game_id"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getPlayerUUID(), testData.coreGameSession.getPlayerUuid(), "db.core_game_session.player_uuid"),
                    () -> assertEquals(GameSessionMode.REAL.getId(), testData.coreGameSession.getModeId(), "db.core_game_session.mode_id"),
                    () -> assertNotNull(testData.coreGameSession.getPlayerIp(), "db.core_game_session.player_ip"),
                    () -> assertEquals(testData.launchGameRequestBody.getLanguage(), testData.coreGameSession.getLanguage(), "db.core_game_session.language"),
                    () -> assertEquals(testData.launchGameRequestBody.getReturnUrl(), testData.coreGameSession.getReturnUrl(), "db.core_game_session.return_url"),
                    () -> assertNotNull(testData.coreGameSession.getStartedAt(), "db.core_game_session.started_at"),
                    () -> assertNotNull(testData.coreGameSession.getUuid(), "db.core_game_session.uuid"),
                    () -> assertNotNull(testData.coreGameSession.getUserAgent(), "db.core_game_session.user_agent"),
                    () -> assertNull(testData.coreGameSession.getPlayerBonusId(), "db.core_game_session.player_bonus_id"),
                    () -> assertEquals(platformNodeId, testData.coreGameSession.getNodeUuid(), "db.core_game_session.node_uuid"),
                    () -> assertNull(testData.coreGameSession.getParentSessionUuid(), "db.core_game_session.parent_session_uuid"),
                    () -> assertNull(testData.coreGameSession.getConversionCurrency(), "db.core_game_session.conversion_currency")
            );
        });

        step("Kafka: Проверка поступления события GameSessionStart", () -> {
            var kafkaMessage = gameSessionKafkaClient.expectGameSessionStartMessage(
                    testData.coreGameSession.getUuid()
            );

            assertAll(
                    () -> assertEquals("wallet.gameSession", kafkaMessage.getMessage().getEventType(), "kafka.game_session.event_type"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getPlayerUUID(), kafkaMessage.getPlayerId(), "kafka.game_session.player_id"),
                    () -> assertTrue(kafkaMessage.getPlayerBonusUuid().isEmpty(), "kafka.game_session.player_bonus_uuid"),
                    () -> assertEquals(platformNodeId, kafkaMessage.getNodeId(), "kafka.game_session.node_id"),
                    () -> assertEquals(testData.coreGameSession.getUuid(), kafkaMessage.getId(), "kafka.game_session.id"),
                    () -> assertEquals(testData.coreGameSession.getPlayerIp(), kafkaMessage.getIp(), "kafka.game_session.ip"),
                    () -> assertEquals(testData.gameProvider.getUuid(), kafkaMessage.getProviderId(), "kafka.game_session.provider_id"),
                    () -> assertEquals(testData.gameProvider.getExternalUuid(), kafkaMessage.getProviderExternalId(), "kafka.game_session.provider_external_id"),
                    () -> assertNotNull(kafkaMessage.getGameTypeName(), "kafka.game_session.game_type_name"),
                    () -> assertEquals(testData.coreGame.getUuid(), kafkaMessage.getGameId(), "kafka.game_session.game_id"),
                    () -> assertEquals(testData.coreGame.getExternalUuid(), kafkaMessage.getGameExternalId(), "kafka.game_session.game_external_id"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getCurrency(), kafkaMessage.getCurrency(), "kafka.game_session.currency"),
                    () -> assertNotNull(kafkaMessage.getStartDate(), "kafka.game_session.start_date"),
                    () -> assertEquals(GameSessionMode.REAL.getName(), kafkaMessage.getGameMode(), "kafka.game_session.game_mode"),
                    () -> assertEquals(testData.coreGameSession.getUserAgent(), kafkaMessage.getUseragent(), "kafka.game_session.user_agent"),
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getWalletUUID(), kafkaMessage.getWalletUuid(), "kafka.game_session.wallet_uuid"),
                    () -> assertEquals(secretKey, kafkaMessage.getSecretKey(), "kafka.game_session.secret_key"),
                    () -> assertNotNull(kafkaMessage.getTypeId(), "kafka.game_session.type_id"),
                    () -> assertNotNull(kafkaMessage.getCategoryId(), "kafka.game_session.category_id")
            );
        });

        step("DB Wallet: Получение данных игровой сессии из БД", () -> {
            testData.walletGameSession = walletDatabaseClient.findSingleGameSessionByPlayerUuidOrFail(
                    testData.registeredPlayer.getWalletData().getPlayerUUID());

            assertAll(
                    () -> assertEquals(testData.registeredPlayer.getWalletData().getWalletUUID(), testData.walletGameSession.getWalletUuid(), "db.wallet_game_session.wallet_uuid"),
                    () -> assertEquals(testData.coreGameSession.getUuid(), testData.walletGameSession.getGameSessionUuid(), "db.wallet_game_session.game_session_uuid"),
                    () -> assertEquals(secretKey, testData.walletGameSession.getSecretKey(), "db.wallet_game_session.secret_key"),
                    () -> assertTrue(testData.walletGameSession.getPlayerBonusUuid().isEmpty(), "db.wallet_game_session.player_bonus_uuid"),
                    () -> assertEquals(testData.gameProvider.getUuid(), testData.walletGameSession.getProviderUuid(), "db.wallet_game_session.provider_uuid"),
                    () -> assertEquals(testData.gameProvider.getExternalUuid(), testData.walletGameSession.getProviderExternalUuid(), "db.wallet_game_session.provider_external_uuid"),
                    () -> assertEquals(testData.coreGame.getUuid(), testData.walletGameSession.getGameUuid(), "db.wallet_game_session.game_uuid"),
                    () -> assertEquals(testData.coreGame.getExternalUuid(), testData.walletGameSession.getGameExternalUuid(), "db.wallet_game_session.game_external_uuid"),
                    () -> assertNotNull(testData.walletGameSession.getTypeUuid(), "db.wallet_game_session.type_uuid"),
                    () -> assertNotNull(testData.walletGameSession.getCategoryUuid(), "db.wallet_game_session.category_uuid"),
                    () -> assertEquals(platformNodeId, testData.walletGameSession.getNodeUuid(), "db.wallet_game_session.node_uuid"),
                    () -> assertTrue(testData.walletGameSession.getGameCurrency().isEmpty(), "db.wallet_game_session.game_currency")
            );
        });
    }
}