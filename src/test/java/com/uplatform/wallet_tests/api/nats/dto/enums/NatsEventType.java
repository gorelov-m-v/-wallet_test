package com.uplatform.wallet_tests.api.nats.dto.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NatsEventType {

    BETTED_FROM_GAMBLE("betted_from_gamble"),
    WON_FROM_GAMBLE("won_from_gamble"),
    REFUNDED_FROM_GAMBLE("refunded_from_gamble"),
    ROLLBACKED_FROM_GAMBLE("rollbacked_from_gamble"),
    TOURNAMENT_WON_FROM_GAMBLE("tournament_won_from_gamble"),
    BALANCE_ADJUSTED("balance_adjusted"),
    SETTING_PREVENT_GAMBLE_SETTED("setting_prevent_gamble_setted"),
    LIMIT_CHANGED_V2("limit_changed_v2"),
    BETTED_FROM_IFRAME("betted_from_iframe"),
    WON_FROM_IFRAME("won_from_iframe"),
    LOOSED_FORM_IFRAME("loosed_from_iframe"),
    REFUNDED_FROM_IFRAME("refunded_from_iframe"),
    RECALCULATED_FROM_IFRAME("recalculated_from_iframe"),
    BLOCK_AMOUNT_STARTED("block_amount_started"),
    BLOCK_AMOUNT_REVOKED("block_amount_revoked"),

    UNKNOWN("unknown");

    private final String headerValue;
}