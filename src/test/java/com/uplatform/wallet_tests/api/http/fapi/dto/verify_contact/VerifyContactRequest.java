package com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VerifyContactRequest {

    @JsonProperty("contact")
    private String contact;

    @JsonProperty("code")
    private String code;
}