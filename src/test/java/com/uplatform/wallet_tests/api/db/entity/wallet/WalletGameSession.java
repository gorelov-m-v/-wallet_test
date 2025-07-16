package com.uplatform.wallet_tests.api.db.entity.wallet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.util.Objects;

@Entity
@Table(name = "game_session")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WalletGameSession {

    @Id
    @Column(name = "game_session_uuid")
    private String gameSessionUuid;

    @Column(name = "wallet_uuid")
    private String walletUuid;

    @Column(name = "secret_key")
    private String secretKey;

    @Column(name = "player_bonus_uuid")
    private String playerBonusUuid;

    @Column(name = "provider_uuid")
    private String providerUuid;

    @Column(name = "provider_external_uuid")
    private String providerExternalUuid;

    @Column(name = "game_uuid")
    private String gameUuid;

    @Column(name = "game_external_uuid")
    private String gameExternalUuid;

    @Column(name = "type_uuid")
    private String typeUuid;

    @Column(name = "category_uuid")
    private String categoryUuid;

    @Column(name = "player_uuid")
    private String playerUuid;

    @Column(name = "node_uuid")
    private String nodeUuid;

    @Column(name = "game_currency")
    private String gameCurrency;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WalletGameSession that = (WalletGameSession) o;
        return Objects.equals(gameSessionUuid, that.gameSessionUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameSessionUuid);
    }
}