package com.uplatform.wallet_tests.api.http.cap.dto.update_verification_status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVerificationStatusRequest {
    private String note;
    private String reason;
    private Integer status;
}
