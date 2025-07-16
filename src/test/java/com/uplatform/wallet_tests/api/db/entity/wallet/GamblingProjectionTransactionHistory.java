package com.uplatform.wallet_tests.api.db.entity.wallet;

import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperationConverter;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "gambling_projection_transaction_history")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GamblingProjectionTransactionHistory {

    @Id
    @Column(name = "uuid")
    private String uuid;

    @Column(name = "player_uuid")
    private String playerUuid;

    @Column(name = "date")
    private Integer date;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private NatsGamblingTransactionType type;

    @Column(name = "operation")
    @Convert(converter = NatsGamblingTransactionOperationConverter.class)
    private NatsGamblingTransactionOperation operation;

    @Column(name = "game_uuid")
    private String gameUuid;

    @Column(name = "game_session_uuid")
    private String gameSessionUuid;

    @Column(name = "currency")
    private String currency;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "created_at")
    private Integer createdAt;

    @Column(name = "seqnumber")
    private Long seqnumber;

    @Column(name = "bet_uuid")
    private String betUuid;

    @Column(name = "provider_round_closed")
    private boolean providerRoundClosed;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GamblingProjectionTransactionHistory that = (GamblingProjectionTransactionHistory) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}