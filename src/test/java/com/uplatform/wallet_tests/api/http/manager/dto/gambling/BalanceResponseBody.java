package com.uplatform.wallet_tests.api.http.manager.dto.gambling;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponseBody {
    private BigDecimal balance;
}