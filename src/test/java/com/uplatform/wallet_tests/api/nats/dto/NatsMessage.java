package com.uplatform.wallet_tests.api.nats.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import java.time.OffsetDateTime;

@Getter
@ToString
@Builder
public class NatsMessage<T> {
    private final T payload;
    private final String subject;
    private final String type;
    private final long sequence;
    private final OffsetDateTime timestamp;
}