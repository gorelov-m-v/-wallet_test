package com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateBlockAmountResponse {
    private String transactionId;
    private String currency;
    private BigDecimal amount;
    private String reason;
    private String userId;
    private String userName;
    private long createdAt;
}