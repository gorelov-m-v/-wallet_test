package com.uplatform.wallet_tests.api.db.entity.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "wallet")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CoreWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "player_uuid", nullable = false)
    private String playerUuid;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false)
    private int createdAt;

    @Column(name = "updated_at", nullable = false)
    private int updatedAt;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_basic", nullable = false)
    private boolean isBasic;

    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(name = "wallet_status", nullable = false)
    private byte walletStatus;

    @Column(name = "wallet_type", nullable = false)
    private byte walletType;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CoreWallet wallet = (CoreWallet) o;
        if (id != null && wallet.id != null) {
            return Objects.equals(id, wallet.id);
        }
        return Objects.equals(uuid, wallet.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id != null ? id : uuid);
    }
}