package com.uplatform.wallet_tests.api.db.entity.wallet;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BettingProjectionIframeHistoryId implements Serializable {
    private String uuid;
    private Long seq;
}