package com.uplatform.wallet_tests.api.db.entity.core;

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
public class CoreGameSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "wallet_id")
    private Integer walletId;

    @Column(name = "game_id")
    private int gameId;

    @Column(name = "player_uuid")
    private String playerUuid;

    @Column(name = "mode_id")
    private short modeId;

    @Column(name = "player_ip")
    private String playerIp;

    @Column(name = "language")
    private String language;

    @Column(name = "return_url", columnDefinition = "TEXT")
    @Lob
    private String returnUrl;

    @Column(name = "game_url", columnDefinition = "TEXT")
    @Lob
    private String gameUrl;

    @Column(name = "currency")
    private String currency;

    @Column(name = "started_at")
    private Integer startedAt;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "player_bonus_id")
    private Integer playerBonusId;

    @Column(name = "node_uuid")
    private String nodeUuid;

    @Column(name = "parent_session_uuid", columnDefinition = "char(36)")
    private String parentSessionUuid;

    @Column(name = "conversion_currency")
    private String conversionCurrency;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CoreGameSession that)) {
            return false;
        }

        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}