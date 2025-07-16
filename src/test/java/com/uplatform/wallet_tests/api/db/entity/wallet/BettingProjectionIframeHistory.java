package com.uplatform.wallet_tests.api.db.entity.wallet;

import com.uplatform.wallet_tests.api.db.entity.wallet.converter.CouponCalcStatusConverter;
import com.uplatform.wallet_tests.api.db.entity.wallet.converter.CouponStatusConverter;
import com.uplatform.wallet_tests.api.db.entity.wallet.converter.CouponTypeConverter;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponCalcStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponStatus;
import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "betting_projection_iframe_history")
@IdClass(BettingProjectionIframeHistoryId.class)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class BettingProjectionIframeHistory {

    @Id
    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    @Id
    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "wallet_uuid", nullable = false, length = 36)
    private String walletUuid;

    @Column(name = "player_uuid", nullable = false, length = 36)
    private String playerUuid;

    @Column(name = "coupon_type", nullable = false)
    @Convert(converter = CouponTypeConverter.class)
    private CouponType couponType;

    @Column(name = "coupon_status", nullable = false)
    @Convert(converter = CouponStatusConverter.class)
    private CouponStatus couponStatus;

    @Column(name = "coupon_calc_status", nullable = false)
    @Convert(converter = CouponCalcStatusConverter.class)
    private CouponCalcStatus couponCalcStatus;

    @Column(name = "bet_id", nullable = false)
    private Long betId;

    @Column(name = "bet_info", nullable = false, columnDefinition = "json")
    private String betInfo;

    @Column(name = "amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(name = "total_coeff", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalCoeff;

    @Column(name = "bet_time", nullable = false)
    private Integer betTime;

    @Column(name = "modified_at", nullable = false)
    private Integer modifiedAt;

    @Column(name = "created_at", nullable = false)
    private Integer createdAt;

    @Column(name = "prev_coeff", precision = 18, scale = 6)
    private BigDecimal prevCoeff;

    @Column(name = "source_coeff", nullable = false, precision = 18, scale = 6)
    private BigDecimal sourceCoeff;

    @Column(name = "amount_delta", nullable = false, precision = 36, scale = 18)
    private BigDecimal amountDelta;

    @Column(name = "win_sum", nullable = false, precision = 36, scale = 18)
    private BigDecimal winSum;

    @Column(name = "coupon_created_at")
    private Integer couponCreatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BettingProjectionIframeHistory that = (BettingProjectionIframeHistory) o;
        return Objects.equals(uuid, that.uuid) && Objects.equals(seq, that.seq);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, seq);
    }
}