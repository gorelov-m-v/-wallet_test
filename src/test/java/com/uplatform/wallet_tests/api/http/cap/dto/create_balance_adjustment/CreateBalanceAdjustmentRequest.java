package com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment;

import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBalanceAdjustmentRequest {
    private String currency;
    private BigDecimal amount;
    private ReasonType reason;
    private OperationType operationType;
    private DirectionType direction;
    private String comment;
}