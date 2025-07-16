package com.uplatform.wallet_tests.api.db.entity.wallet;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wallet")
@Data
@NoArgsConstructor
public class Wallet {
    @Id
    @Column(name = "uuid", length = 36, nullable = false)
    private String uuid;

    @Column(name = "player_uuid", length = 36, nullable = false)
    private String playerUuid;

    @Column(name = "is_gambling_active", nullable = false)
    private boolean gamblingActive;

    @Column(name = "is_betting_active", nullable = false)
    private boolean bettingActive;
}
