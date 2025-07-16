package com.uplatform.wallet_tests.api.http.fapi.dto.launch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LaunchGameRequestBody {

    @JsonProperty("language")
    private String language;

    @JsonProperty("returnUrl")
    private String returnUrl;
}