package com.uplatform.wallet_tests.api.http.fapi.dto.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRequest {
    private String number;
    private String type;
    private String issuedDate;
    private String expiryDate;
}
