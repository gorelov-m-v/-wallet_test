package com.uplatform.wallet_tests.api.http.fapi.dto.launch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LaunchGameResponseBody {

    @JsonProperty("url")
    private String url;
}