package com.uplatform.wallet_tests.api.http.cap.dto.cancel_kyc_check;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelKycCheckRequest {

    @JsonProperty("kycCheckProceed")
    private boolean kycCheckProceed;
}