package com.uplatform.wallet_tests.api.http.cap.dto.get_block_amount_list;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BlockAmountListItem {
    private String transactionId;
    private String currency;
    private BigDecimal amount;
    private String reason;
    private String userId;
    private String userName;
    private Long   createdAt;
    private String walletId;
    private String playerId;
}
