package com.uplatform.wallet_tests.api.db.entity.wallet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "player_threshold_win")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PlayerThresholdWin {
    @Id
    @Column(name = "player_uuid")
    private String playerUuid;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "updated_at")
    private Long updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PlayerThresholdWin that = (PlayerThresholdWin) o;
        return Objects.equals(playerUuid, that.playerUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUuid);
    }
}