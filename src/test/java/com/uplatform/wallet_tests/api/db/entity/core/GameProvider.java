package com.uplatform.wallet_tests.api.db.entity.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.util.Objects;
import java.util.List;
import com.uplatform.wallet_tests.api.db.entity.core.converter.CurrenciesConverter;

@Entity
@Table(name = "game_provider")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GameProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(name = "external_uuid", nullable = false)
    private String externalUuid;

    @Column(nullable = false)
    private String name;

    @Column(name = "game_contract_id", nullable = false)
    private int gameContractId;

    @Column(name = "status_id", nullable = false)
    private short statusId;

    @Column(name = "created_at", nullable = false)
    private int createdAt;

    @Column(name = "updated_at", nullable = false)
    private int updatedAt;

    @Column(name = "deleted_at")
    private Integer deletedAt;

    @Column(name = "default_currency")
    private String defaultCurrency;

    @Column(name = "currencies")
    @Convert(converter = CurrenciesConverter.class)
    private List<String> currencies;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GameProvider that = (GameProvider) o;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id != null ? id : uuid);
    }
}