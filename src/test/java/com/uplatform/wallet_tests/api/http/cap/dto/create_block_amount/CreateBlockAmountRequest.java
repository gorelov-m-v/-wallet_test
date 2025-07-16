package com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateBlockAmountRequest {
    private String reason;
    private String amount;
    private String currency;
}