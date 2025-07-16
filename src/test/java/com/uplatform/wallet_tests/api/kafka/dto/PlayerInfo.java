package com.uplatform.wallet_tests.api.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.Gender;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInfo {

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("nodeId")
    private String nodeId;

    @JsonProperty("projectGroupId")
    private String projectGroupId;

    @JsonProperty("externalId")
    private String externalId;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("middleName")
    private String middleName;

    @JsonProperty("gender")
    private Gender gender;

    @JsonProperty("birthday")
    private String birthday;

    @JsonProperty("region")
    private String region;

    @JsonProperty("postalCode")
    private String postalCode;

    @JsonProperty("address")
    private String address;

    @JsonProperty("city")
    private String city;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("country")
    private String country;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("createdAt")
    private Long createdAt;

    @JsonProperty("registrationIp")
    private String registrationIp;

    @JsonProperty("iban")
    private String iban;

    @JsonProperty("personalId")
    private String personalId;

    @JsonProperty("placeOfWork")
    private String placeOfWork;

    @JsonProperty("activitySectorInput")
    private String activitySectorInput;

    @JsonProperty("activitySectorAlias")
    private String activitySectorAlias;

    @JsonProperty("avgMonthlySalaryEURInput")
    private String avgMonthlySalaryEURInput;

    @JsonProperty("avgMonthlySalaryEURAlias")
    private String avgMonthlySalaryEURAlias;

    @JsonProperty("jobAlias")
    private String jobAlias;

    @JsonProperty("jobInput")
    private String jobInput;

    @JsonProperty("isPoliticallyInvolved")
    private Boolean isPoliticallyInvolved;

    @JsonProperty("isKYCVerified")
    private Boolean isKYCVerified;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("bonusChoice")
    private String bonusChoice;

    @JsonProperty("profession")
    private String profession;

    @JsonProperty("promoCode")
    private String promoCode;

    @JsonProperty("ip")
    private String ip;
}