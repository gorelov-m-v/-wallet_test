package com.uplatform.wallet_tests.api.http.fapi.dto.get_games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FapiGame {

    @JsonProperty("id")
    private String id;

    @JsonProperty("alias")
    private String alias;

    @JsonProperty("name")
    private String name;

    @JsonProperty("image")
    private String image;

    @JsonProperty("providerName")
    private String providerName;

    @JsonProperty("ruleResource")
    private String ruleResource;

    @JsonProperty("hasDemo")
    private boolean hasDemo;

    @JsonProperty("canPlayDemo")
    private boolean canPlayDemo;

    @JsonProperty("brand")
    private Brand brand;
}