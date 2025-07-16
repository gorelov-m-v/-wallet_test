package com.uplatform.wallet_tests.api.http.fapi.dto.check;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenCheckRequest {
    private String username;
    private String password;
}