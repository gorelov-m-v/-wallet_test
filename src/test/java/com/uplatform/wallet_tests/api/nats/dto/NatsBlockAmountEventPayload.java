package com.uplatform.wallet_tests.api.nats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountStatus;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBlockAmountType;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class NatsBlockAmountEventPayload {
    private String uuid;
    private NatsBlockAmountStatus status;
    private BigDecimal amount;
    private String reason;
    private NatsBlockAmountType type;
    @JsonProperty("expired_at")
    private Long expiredAt;
    @JsonProperty("user_uuid")
    private String userUuid;
    @JsonProperty("user_name")
    private String userName;
    @JsonProperty("created_at")
    private Long createdAt;
}
