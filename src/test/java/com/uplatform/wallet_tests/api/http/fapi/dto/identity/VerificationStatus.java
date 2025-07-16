package com.uplatform.wallet_tests.api.http.fapi.dto.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatus {
    private Integer status;
    private String documentId;
    private String reason;
    private String type;
    private String documentType;
    private String documentNumber;
    @JsonProperty("expireDate")
    private Long expireDate;
}
