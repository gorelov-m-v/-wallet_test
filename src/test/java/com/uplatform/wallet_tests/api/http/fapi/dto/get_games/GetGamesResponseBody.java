package com.uplatform.wallet_tests.api.http.fapi.dto.get_games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetGamesResponseBody {

    @JsonProperty("total")
    private int total;

    @JsonProperty("games")
    private List<FapiGame> games;
}