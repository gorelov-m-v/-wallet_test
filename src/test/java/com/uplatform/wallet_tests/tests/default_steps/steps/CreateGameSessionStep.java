package com.uplatform.wallet_tests.tests.default_steps.steps;

import com.uplatform.wallet_tests.api.db.WalletDatabaseClient;
import com.uplatform.wallet_tests.api.db.entity.wallet.WalletGameSession;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.get_games.GetGamesResponseBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.launch.LaunchGameResponseBody;
import com.uplatform.wallet_tests.tests.default_steps.dto.GameLaunchData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Random;

import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.*;

@Component
@RequiredArgsConstructor
public class CreateGameSessionStep {

    private static final Random RANDOM = new Random();

    private final FapiClient publicClient;
    private final WalletDatabaseClient walletDatabaseClient;

    private static final class GameSessionTestData {
        ResponseEntity<GetGamesResponseBody> gamesResponse;
        ResponseEntity<LaunchGameResponseBody> launchResponse;
        WalletGameSession dbGameSession;
    }

    public GameLaunchData createGameSession(RegisteredPlayerData playerData) {
        Objects.requireNonNull(playerData, "RegisteredPlayerData cannot be null");
        Objects.requireNonNull(playerData.getAuthorizationResponse(), "AuthorizationResponse in RegisteredPlayerData cannot be null");
        Objects.requireNonNull(playerData.getAuthorizationResponse().getBody(), "AuthorizationResponse body in RegisteredPlayerData cannot be null");
        Objects.requireNonNull(playerData.getAuthorizationResponse().getBody().getToken(), "Token in RegisteredPlayerData cannot be null");
        Objects.requireNonNull(playerData.getWalletData(), "WalletData in RegisteredPlayerData cannot be null");
        Objects.requireNonNull(playerData.getWalletData().getPlayerUUID(), "PlayerUUID in WalletData cannot be null");

        final GameSessionTestData testData = new GameSessionTestData();

        step("1. Public API: Получение списка игр и выбор случайной", () -> {
            testData.gamesResponse = this.publicClient.getGames(1, 5);
            assertEquals(HttpStatus.OK, testData.gamesResponse.getStatusCode(), "fapi.get_games.status_code");
            assertNotNull(testData.gamesResponse.getBody(), "fapi.get_games.body_not_null");
            assertNotNull(testData.gamesResponse.getBody().getGames(), "fapi.get_games.list_not_null");
            assertFalse(testData.gamesResponse.getBody().getGames().isEmpty(), "fapi.get_games.list_not_empty");
        });

        step("2. Public API: Запуск выбранной игры", () -> {
            var games = testData.gamesResponse.getBody().getGames();
            var selectedGame = games.get(RANDOM.nextInt(games.size()));
            assertNotNull(selectedGame.getAlias(), "fapi.launch_game.selected_game_alias");

            var requestBody = LaunchGameRequestBody.builder()
                    .language("en")
                    .returnUrl("https://beta-09.b2bdev.pro/casino")
                    .build();

            testData.launchResponse = this.publicClient.launchGame(
                    selectedGame.getAlias(),
                    playerData.getAuthorizationResponse().getBody().getToken(),
                    requestBody);

            assertEquals(HttpStatus.OK, testData.launchResponse.getStatusCode(), "fapi.launch_game.status_code");
            assertNotNull(testData.launchResponse.getBody(), "fapi.launch_game.body_not_null");
            assertNotNull(testData.launchResponse.getBody().getUrl(), "fapi.launch_game.url_not_null");
        });

        step("3. DB Wallet: Получение данных игровой сессии из БД", () -> {
            var playerUuid = playerData.getWalletData().getPlayerUUID();
            testData.dbGameSession = this.walletDatabaseClient.findSingleGameSessionByPlayerUuidOrFail(playerUuid);
            assertNotNull(testData.dbGameSession, "db.wallet.game_session.not_found");
            assertNotNull(testData.dbGameSession.getGameSessionUuid(), "db.wallet.game_session.uuid");
        });

        return new GameLaunchData(
                testData.dbGameSession,
                testData.launchResponse
        );
    }
}