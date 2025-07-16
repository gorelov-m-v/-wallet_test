package com.uplatform.wallet_tests.api.redis.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import java.util.Optional;

@Getter
@Builder
@ToString
public class WalletFilterCriteria {
    @Builder.Default
    private Optional<String> currency = Optional.empty();
    @Builder.Default
    private Optional<Integer> type = Optional.empty();
    @Builder.Default
    private Optional<Integer> status = Optional.empty();
}