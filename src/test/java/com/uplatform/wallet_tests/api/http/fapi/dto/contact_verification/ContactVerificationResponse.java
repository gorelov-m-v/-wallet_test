package com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactVerificationResponse {

    @JsonProperty("codeExpire")
    private Long codeExpire;

}