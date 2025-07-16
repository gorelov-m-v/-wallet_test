package com.uplatform.wallet_tests.api.http.cap.dto.errors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.uplatform.wallet_tests.tests.util.utils.FlexibleErrorMapDeserializer;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationErrorResponse {
    private int code;
    private String message;

    @JsonDeserialize(using = FlexibleErrorMapDeserializer.class)
    private Map<String, List<String>> errors;
}
