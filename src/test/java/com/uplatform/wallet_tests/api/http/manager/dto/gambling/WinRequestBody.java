package com.uplatform.wallet_tests.api.http.manager.dto.gambling;

import com.uplatform.wallet_tests.api.nats.dto.enums.NatsGamblingTransactionOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WinRequestBody {
    private String sessionToken;
    private BigDecimal amount;
    private String transactionId;
    private NatsGamblingTransactionOperation type;
    private String roundId;
    private Boolean roundClosed;
}
