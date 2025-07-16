package com.uplatform.wallet_tests.api.http.fapi.dto.player_restrictions;

import com.uplatform.wallet_tests.api.http.fapi.dto.enums.RestrictionExpireType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRestrictionsRequest {
    private RestrictionExpireType expireType;
}