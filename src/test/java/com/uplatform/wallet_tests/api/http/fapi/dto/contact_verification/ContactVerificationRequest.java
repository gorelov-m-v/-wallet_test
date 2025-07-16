package com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContactVerificationRequest {
    private String contact;
    private ContactType type;
}