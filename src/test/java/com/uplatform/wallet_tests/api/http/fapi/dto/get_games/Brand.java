package com.uplatform.wallet_tests.api.http.fapi.dto.get_games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Brand {

    @JsonProperty("id")
    private String id;

    @JsonProperty("alias")
    private String alias;

    @JsonProperty("name")
    private String name;

    @JsonProperty("icon")
    private String icon;

    @JsonProperty("logo")
    private String logo;

    @JsonProperty("colorLogo")
    private String colorLogo;
}