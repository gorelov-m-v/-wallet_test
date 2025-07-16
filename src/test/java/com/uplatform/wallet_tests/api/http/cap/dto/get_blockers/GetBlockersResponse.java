package com.uplatform.wallet_tests.api.http.cap.dto.get_blockers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetBlockersResponse {
    private boolean gamblingEnabled;
    private boolean bettingEnabled;
}