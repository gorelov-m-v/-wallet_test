package com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyContactTypedRequest {
    @JsonProperty("contact")
    private String contact;

    @JsonProperty("type")
    private ContactType type;

    @JsonProperty("code")
    private String code;
}
