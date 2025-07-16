package com.uplatform.wallet_tests.api.http.manager.dto.betting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.enums.BettingErrorCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MakePaymentResponse {
    private boolean success;
    private String description;
    private int errorCode;
    @JsonIgnore
    public BettingErrorCode getErrorCodeAsEnum() {
        return BettingErrorCode.findByCode(this.errorCode);
    }
}