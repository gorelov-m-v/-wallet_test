package com.uplatform.wallet_tests.api.http.fapi.dto.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.BonusChoiceType;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.Gender;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class FullRegistrationRequestBody {

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("country")
    private String country;

    @JsonProperty("bonusChoice")
    private BonusChoiceType bonusChoice;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("phoneConfirmation")
    private String phoneConfirmation;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("birthday")
    private String birthday;

    @JsonProperty("gender")
    private Gender gender;

    @JsonProperty("personalId")
    private String personalId;

    @JsonProperty("iban")
    private String iban;

    @JsonProperty("city")
    private String city;

    @JsonProperty("permanentAddress")
    private String permanentAddress;

    @JsonProperty("postalCode")
    private String postalCode;

    @JsonProperty("profession")
    private String profession;

    @JsonProperty("password")
    private String password;

    @JsonProperty("rulesAgreement")
    private boolean rulesAgreement;

    @JsonProperty("context")
    private Map<String, Object> context;
}