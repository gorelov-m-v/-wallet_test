package com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyContactResponse {

    @JsonProperty("hash")
    private String hash;
}