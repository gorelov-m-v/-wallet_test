package com.uplatform.wallet_tests.tests.util.utils;

import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NatsEnumMapper {

    public static Integer mapDirectionToNatsInt(DirectionType direction) {
        if (direction == null) {
            return null;
        }
        return switch (direction) {
            case INCREASE -> 1;
            case DECREASE -> 0;
            case UNKNOWN, EMPTY -> null;
        };
    }

    public static Integer mapOperationTypeToNatsInt(OperationType operationType) {
        if (operationType == null) {
            return null;
        }
        return switch (operationType) {
            case CORRECTION -> 0;
            case DEPOSIT -> 1;
            case WITHDRAWAL -> 2;
            case GIFT -> 3;
            case REFERRAL_COMMISSION -> 4;
            case CASHBACK -> 5;
            case TOURNAMENT_PRIZE -> 6;
            case JACKPOT -> 7;
            case UNKNOWN, EMPTY -> null;
        };
    }

    public static Integer mapReasonToNatsInt(ReasonType reasonType) {
        if (reasonType == null) {
            return null;
        }
        return switch (reasonType) {
            case MALFUNCTION -> 0;
            case OPERATIONAL_MISTAKE -> 1;
            case BALANCE_CORRECTION -> 2;
            case UNKNOWN, EMPTY -> null;
        };
    }
}