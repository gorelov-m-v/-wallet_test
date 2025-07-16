package com.uplatform.wallet_tests.api.db.entity.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.util.Objects;

@Entity
@Table(name = "game")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CoreGame {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String uuid;

    @Column(name = "external_uuid", nullable = false)
    private String externalUuid;

    @Column(name = "game_provider_id", nullable = false)
    private int gameProviderId;

    @Column(name = "game_type_id", nullable = false)
    private int gameTypeId;

    @Column(name = "status_id", nullable = false)
    private short statusId;

    private String name;
    private String image;
    @Column(name = "custom_image")
    private String customImage;
    @Column(name = "provider_image")
    private String providerImage;
    @Column(name = "original_name")
    private String originalName;
    @Column(name = "original_image")
    private String originalImage;

    @Column(name = "is_mobile", nullable = false)
    private boolean isMobile;

    @Column(name = "rule_resource", nullable = false)
    private String ruleResource;

    @Column(name = "created_at", nullable = false)
    private int createdAt;

    @Column(name = "updated_at", nullable = false)
    private int updatedAt;

    @Column(name = "deleted_at")
    private Integer deletedAt;

    @Column(name = "is_desktop", nullable = false)
    private boolean isDesktop;

    @Column(name = "has_demo", nullable = false)
    private boolean hasDemo;

    @Column(nullable = false)
    private String alias;

    @Column(name = "is_demo_disabled", nullable = false)
    private boolean isDemoDisabled;

    @Column(name = "has_free_spins")
    private Boolean hasFreeSpins;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CoreGame game = (CoreGame) o;
        if (id != null && game.id != null) {
            return Objects.equals(id, game.id);
        }
        return Objects.equals(uuid, game.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id != null ? id : uuid);
    }
}