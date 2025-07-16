package com.uplatform.wallet_tests.api.http.cap.dto.check;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CapTokenCheckResponse {

    @JsonProperty("token")
    private String token;

    @JsonProperty("refreshToken")
    private String refreshToken;
}