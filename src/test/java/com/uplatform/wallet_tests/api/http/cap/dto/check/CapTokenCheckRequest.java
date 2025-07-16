package com.uplatform.wallet_tests.api.http.cap.dto.check;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CapTokenCheckRequest {
    private String username;
    private String password;
}