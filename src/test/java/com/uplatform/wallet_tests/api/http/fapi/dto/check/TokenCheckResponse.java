package com.uplatform.wallet_tests.api.http.fapi.dto.check;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TokenCheckResponse {

    @JsonProperty("token")
    private String token;

    @JsonProperty("refreshToken")
    private String refreshToken;

    public String getToken() {
        return "Bearer " + token;
    }
}