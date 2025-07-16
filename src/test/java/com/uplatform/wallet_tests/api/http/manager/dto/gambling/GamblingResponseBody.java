package com.uplatform.wallet_tests.api.http.manager.dto.gambling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GamblingResponseBody {
    private BigDecimal balance;
    private String transactionId;
}